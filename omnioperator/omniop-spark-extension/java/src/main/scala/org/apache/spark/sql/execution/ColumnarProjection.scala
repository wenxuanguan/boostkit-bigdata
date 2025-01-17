/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2022-2022. All rights reserved.
 */

package org.apache.spark.sql.execution

import java.util.concurrent.TimeUnit.NANOSECONDS
import com.huawei.boostkit.spark.Constant.{IS_ENABLE_JIT, IS_SKIP_VERIFY_EXP}
import com.huawei.boostkit.spark.util.OmniAdaptorUtil.transColBatchToOmniVecs
import nova.hetu.omniruntime.`type`.DataType
import nova.hetu.omniruntime.operator.config.OperatorConfig
import nova.hetu.omniruntime.operator.project.OmniProjectOperatorFactory
import nova.hetu.omniruntime.vector.VecBatch
import org.apache.spark.sql.execution.metric.SQLMetric
import org.apache.spark.sql.execution.util.SparkMemoryUtils.addLeakSafeTaskCompletionListener
import org.apache.spark.sql.execution.vectorized.OmniColumnVector
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.vectorized.ColumnarBatch

/**
 * @since 2022/3/5
 */
object ColumnarProjection {
  def dealPartitionData(numOutputRows: SQLMetric, numOutputVecBatchs: SQLMetric,
                        addInputTime: SQLMetric,
                        omniCodegenTime: SQLMetric,
                        getOutputTime: SQLMetric, omniInputTypes: Array[DataType],
                        omniExpressions: Array[String], iter: Iterator[ColumnarBatch],
                        schema: StructType): Iterator[ColumnarBatch] = {
    val startCodegen = System.nanoTime()
    val projectOperatorFactory = new OmniProjectOperatorFactory(omniExpressions, omniInputTypes, 1, new OperatorConfig(IS_ENABLE_JIT, IS_SKIP_VERIFY_EXP))
    val projectOperator = projectOperatorFactory.createOperator
    omniCodegenTime += NANOSECONDS.toMillis(System.nanoTime() - startCodegen)
    // close operator
    addLeakSafeTaskCompletionListener[Unit](_ => {
      projectOperator.close()
    })

    new Iterator[ColumnarBatch] {
      private var results: java.util.Iterator[VecBatch] = _

      override def hasNext: Boolean = {
        while ((results == null || !results.hasNext) && iter.hasNext) {
          val batch = iter.next()
          val input = transColBatchToOmniVecs(batch)
          val vecBatch = new VecBatch(input, batch.numRows());
          val startInput = System.nanoTime()
          projectOperator.addInput(vecBatch)
          addInputTime += NANOSECONDS.toMillis(System.nanoTime() - startInput)

          val startGetOp = System.nanoTime()
          results = projectOperator.getOutput
          getOutputTime += NANOSECONDS.toMillis(System.nanoTime() - startGetOp)
        }
        if (results == null) {
          false
        } else {
          val startGetOp: Long = System.nanoTime()
          var hasNext = results.hasNext
          getOutputTime += NANOSECONDS.toMillis(System.nanoTime() - startGetOp)
          hasNext
        }
      }

      override def next(): ColumnarBatch = {
        val startGetOp = System.nanoTime()
        val result = results.next()
        getOutputTime += NANOSECONDS.toMillis(System.nanoTime() - startGetOp)

        val vectors: Seq[OmniColumnVector] = OmniColumnVector.allocateColumns(
          result.getRowCount, schema, false)
        vectors.zipWithIndex.foreach { case (vector, i) =>
          vector.reset()
          vector.setVec(result.getVectors()(i))
        }
        if(numOutputRows != null) {
          numOutputRows += result.getRowCount
        }
        if (numOutputVecBatchs != null) {
          numOutputVecBatchs += 1
        }
        result.close()
        new ColumnarBatch(vectors.toArray, result.getRowCount)
      }
    }
  }
}
