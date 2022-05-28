/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution

import java.util.concurrent.TimeUnit. NANOSECONDS
import com.huawei.boostkit.spark.Constant. (IS_ENABLE_JIT, IS_SKIP_VERIFY_EXP)
import com.huawei.boostkit.spark.expression. OmniExpressionAdaptor.
import com.huawei.boostkit.spark.util.OmniAdaptorUtil.transColBatchToOmnivecs
import nova.hetu.omniruntime. 'type'. DataType
import nova.hetu.omniruntime. constants. FunctionType
import nova.hetu. omniruntime.operator.config.OperatorConfig
import nova.hetu.omniruntime.operator.window. OmniwindowwithExproperatorFactory
import nova. hetu.omniruntime.vector. VecBatch
import org. apache.spark.rdd. RDD
import org.apache.spark.sql.catalyst. InternalRow
import org.apache.spark.sql.catalyst.expressions.
import org.apache.spark.sql.catalyst.expressions.aggregate. (AggregateExpression, Average, Count, Max,
Min, Sum]
import org.apache.spark.sql.catalyst.plans.physical. (AllTuples, ClusteredDistribution, Distribution,
Partitioning)
import org.apache.spark.sql.execution.ColumnarProjection.dealPartitionData
import org.apache.spark.sql.execution.metric.SOLMetrics
import org.apache. spark.sql.execution.util.SparkMemoryUtils
import org.apache.spark.sql.execution.vectorized. OmnicolumnVector
import org.apache.spark.sql.execution.window. (WindowExec, windowExecBase)
import org.apache.spark.sql.types. (DecimalType, StructType)
import org.apache.spark.sql.vectorized.ColumnarBatch
case class ColumnarWindowExec (windowExpression: Seq[NamedExpression],
                               partitionSpec: Seq[Expression],
                               orderSpec: Seq[Sortorder], child: SparkPlan)
  extends windowExecBase
override def nodeName: String = "OmniColumnarwindow"
  override def supportsColumnar: Boolean = true
  override lazy val metrics = Map(
  "addInputTime" -> SOLMetrics.createTimingMetric(sparkContext, name= "time in omni addInput"),
  "numInputVecBatchs" -> SOLMetrics.createMetric(sparkContext, name= "number of input vecBatchs"),
  "numInputRows" -> SOLMetrics.createMetric(sparkContext, name = "number of input rows"),
  "omni CodegenTime" -> SOLMetrics.createTimingMetric (sparkContext, name= "time in omni codegen"),
  "getOutputTime" -> SOLMetrics.createTimingMetric(sparkContext, name = "time in omni getoutput"),
  "numoutputRows" -> SOLMetrics.createMetric(sparkContext, name= "number of output rows"),
  "numOutputVecBatchs" -> SOLMetrics.createMetric (sparkContext, name = "number of output vecBatchs"))
  override def output: Seg [Attribute] =
  child.output ++ windowExpression.map (_.toAttribute)
  override def requiredChildnistribution: Seq[Distribution] =1
  if (partitionSpec.isEmpty) 1
  11 Only show warning when the number of bytes is larger than 100 MiB?
  logWarning ( msg= "No Partition Defined for Window operation! Moving all data to a single "
  + "partition, this can cause serious performance degradation.")
  AllTuples :: Nil
  ) else ClusteredDistribution (partitionSpec) :: Nil
  override def requiredchildordering: Seq[Seq[Sortorder]] =
  Seg (partitionSpec.map (Sortorder(_, Ascending) ) ++ orderSpec)
  override def putputordering: Seq[Sortorder] = child.outputordering
  override def outputPartitioning: Partitioning = child.outputPartitioning
  override protected def doexecute (): RDD [InternalRow] = 1
  throw new UnsupportedOperationException (s"This operator doesn't support doExecute () .")
  def checkAggFunInOutDatatype (funcInDataType: org.apache. spark. sql.types. DataType, funcoutDataType: org.apache.spark.sql.types.DataType): Unit =[ throw new UnsupportedoperationException (s"This operator doesn't support doexet 02 A 20 47 X27 def checkAggFunTnoutDataType (funcInDatatype: org.apache. spark.sql.types.Datatype, funcoutDatatype: org.apache. spark.sql. types.DataType): Unit = 1
  1/for decimal, only support decimal64 to decimal128 output
  if (funcInDataType.isInstanceOf [DecimalType] && funcoutDataType.isInstanceof [DecimalTypel)-
  if (!DecimalType.is64BitDecimalType (funcoutDataType.asInstanceOf [Decima1Type]))
  throw new UnsupportedoperationException (s"output only support decimal128 type,
  inDataType:S ( funcInDataType) outDataType: S ( funcoutDataType)")
def buildcheck (): Unit = 1
  val inputColsize = child.outputSet.size
  val sourcetypes = new Array [DataTypel (inputColsize)
  val windowFuntype = new Array [FunctionType] (windowExpression.size)
  var windowArgKeys = new Array [AnyRef] (0)
  val windowFunRetType = new Array [DataType] (windowexpression.size)
  val omniAttrExpsIdMap = getExprIdMap (child.output)
  var attrMap: Map [String, Int] = Map ()
  val inputIter = child.outputset.toIterator
  var i = 0
  while (inputIter.hasNext)
  val inputAttr = inputIter.next ()
  sourceTypes (i) = sparkTypeToomniType (inputattr.datatype, inputattr.metadata)
  attrMap += (inputAttr. name -> i)
  i+=-1
  var windowExpressionWithProject = false
  windowExpression.foreach (x =>
  x.foreach
  case e@windowexpression (function, spec) =>
  windowFunRetType (0) = sparkTypeToomniType (function.dataType)
  function match (
  // AggregatewindowFunction
  case winfunc: windowFunction =>
  windowFunType (0) = toOmniwindowFun Type (winfunc)
  windowargKeys = winfunc.children.map (
  exp => rewriteToOmniJsonExpressionLiteral (exp, omniAttrExpsIdMap) ).toArray
  1/ AggregateExpression
  case aggeAggregateExpression (aggFunc, _-) =>
  windowFunType (0) = toOmniAggFunType (agg)
  windowArgKeys = aggFunc, children.map(
  exp =>
  checkAggFunInOutDataType (function.dataType, exp.dataType)
  rewriteToomniJsonExpressionLiteral (exp, omniAttrExpsIdMap)
  1).toArray
  case=> throw new UnsupportedoperationException (s"Unsupported window function:
  S( function) ")
.lower) ")
if (spec. frameSpecification.isInstanceOf [SpecifiedwindowFrame]) 1
  val winFram = spec.frameSpecification.asInstanceof [SpecifiedwindowFrame]
  if (winFram.lower != UnboundedPreceding) (
  throw new UnsupportedOperationException (s"Unsupported Specified frame_start: S(winFram
  else if (winFram. upper != UnboundedFollowing && winFram.upper != CurrentRow)
  throw new UnsupportedOperationexception (s"Unsupported Specified frame_end: $(winFram
  upper) ")
case_=>
  windowExpressionwithProject = true
  val winExpressions: Seg[Expression] = windowFrameExpressionFactoryPairs. flatMap (_._1)
  val winExptoReferences = winExpressions.zipwithIndex.map [ case (e, i) =>
  1/ Results of window expressions will be on the right side of child's output
  AttributeReference (String. valueof (child.output.size + i), e.datatype, e.nullable) ().toattribute
  val winExpToReferencesMap = winExpressions. zip (winExpToReferences).toMap
  val patchedwindowExpression = windowexpression.map (_.transform (winExpToReferencesMap))
  if (windowExpressionwithProject)
  val finalOut = child.output ++ winExpToReferences
  val projectInputtypes = finalOut.map(
  еxр
  sparkTypeToOmniType XP. throw new UnsupportedoperationException (s"This operator doesn't support doExec 2 A20 A7 x27 def checkAggFunInoutDataType (funcInDatatype: org.apache.spark.sql.types. Datatype, funcoutDataType: org.apache. spark.sgl.types.DataType): Unit =
  1/for decimal, only support decimal64 to decimal128 output
  if (funcInDatarype.isInstanceof [DecimalType] && funcoutpatatype.isInstanceof [DecimalType])
  if (!DecimalType.is64BitDecimalType (funcoutDataType.asInstanceOf [DecimalType]))
  throw new UnsupportedoperationException (s"output only support decimal128 type,
  inDataType:S (funcInDataType) outDataType: S (funcoutDataType)" )
def buildcheck (): Unit = (
  val inputColsize = child. outputSet.size
  val sourcetypes = new Array [DataType] (inputColsize)
  val windowFuntype = new Array [FunctionType] (windowExpression.size)
  var windowArgKeys = new Array [AnyRef] (0)
  val windowFunRetType = new Array [DataType] (windowExpression.size)
  val omniAttrExpsIdMap = getExprIdMap (child.output)
  var attrMap: Map [String, Int] = Map ()
  val inputIter = child. outputset.toIterator
  var i = 0
  while (inputIter.hasNext) (
  val inputAttr = inputIter.next ()
  sourceTypes (i) = sparkTypeToomniType (inputAttr.dataType, inputAttr.metadata)
  attrMap += (inputAttr.name -> i)
  i+=-1
  var windowExpressionwithProject = false
  windowexpression.foreach ( x =>
  x.foreach
  case e@windowExpression (function, spec) =>
  windowFunRetType (0) = sparkTypeToomniType (function.dataType)
  function match (
  1/ AggregatewindowFunction
  case winfunc: WindowFunction =>
  windowFunType (0) = toomniwindowFunType (winfunc)
  windowArgKeys = winfunc.children.map (
  exp => rewriteToOmniJsonExpressionLiteral (exp, omniAttrExpsIdMap)).toArray
  // AggregateExpression
  case aggeAggregateExpression (aggFunc, _--_) =>
  windowFuntype (0) = toomniAggFunType (agg)
  windowArgKeys = aggFunc.children.map (
  exp =>
  checkAggFunInOutDataType (function.datatype, exp.dataType)
  rewriteToomniJsonExpressionLiteral (exp, omniattrExpsIdMap)
  1).toArray
  S(function)")
case_=> throw new UnsupportedOperationException (s"Unsupported window function:
  if (spec.frameSpecification.isInstanceOf [SpecifiedwindowFrame])
  val winFram = spec. frameSpecification.asInstanceOf [SpecifiedWindowFrame]
  if (winFram.lower != UnboundedPreceding)
  throw new UnsupportedoperationException (s"Unsupported specified frame_start: S(winFram
  .lower)") else if (winFram. upper != UnboundedFollowing && winFram. upper != CurrentRow) (
throw new UnsupportedOperationException (s"Unsupported Specified frame_end: S(winFram
  upper)")
case_=>
  windowExpressionwithProject = true
  val winExpressions: Seq[Expression] = windowFrameExpressionFactoryPairs.flatMap (_._1)
  val winExpToReferences = winExpressions.zipwithIndex.map I case (e, i) =>
  // Results of window expressions will be on the right side of child's output
  AttributeReference (String.valueof (child.output.size + i), e.datatype, e.nullable) ().toattribute
  val winExpToReferencesMap = winExpressions.zip (winExpToReferences). toMap
  val patchedwindowExpression = windowExpression.map (_.transform (winExpToReferencesMap))
  if (windowExpressionWithProject) (
  val finalOut = child.output ++ winExptoReferences
  val project Inputtypes = finalout.map(
  exp => spark TypeToOmni Type (exp,datatype, exp.metadata)) if (windowEx
  val finalout = child.output ++ winExpToReferences
  val projectInputTypes = finalout.map (
  exp => sparkTypeToOmniType (exp.datatype, exp.metadata)).toArray
  val projectexpressions: Array [AnyRef] = (child.output + patchedwindowExpression) .map(
  exp => rewriteToOmniJsonExpressionLiteral (exp, getExprIdMap (finalout))).toArray
  checkOmniJsonWhiteList ( filterExpr = "", projectExpressions)
  checkOmniJsonWhiteList ( filterExpr = "", windowArgKeys)
  override def doExecuteColumnar () : RDD [ColumnarBatch] = (
  val addInputTime = longMetric( name = "addInputTime")
  val numInputRows = longMetric ( name = "numInputRows")
  val numInputvecBatchs = longMetric( name = "numInputVecBatchs")
  val omnicodegentime = longMetric ( name = "omniCodegentime")
  val numoutputRows = longMetric ( name = "numOutputRows")
  val numOutputvecBatchs = longMetric ( name = "numOutputvecBatchs")
  val getoutputTime = longMetric ( name = "getoutputTime")
  val inputColsize = child.outputSet.size
  val sourceTypes = new Array[DataType] (inputColsize)
  val sortCols = new Array [Int] (orderSpec.size)
  val ascendings = new Array [Int] (orderSpec.size)
  val nullFirsts = new Array [Int] (orderSpec.size)
  val windowFuntype = new Array [Functiontype] (windowExpression.size)
  val omminPartitionChannels = new Array [Int] (partitionspec.size)
  val preGroupedChannels = new Array [Int] (0)
  var windowArgKeys = new Array [String] (0)
  var windowArgKeysForSkip = new Array [String] (0)
  val windowFunRetType = new Array [DataType] (windowExpression.size)
  val omniAttrExpsIdMap = getExprIdMap (child.output)
  var attrMap: Map [string, Int] = Map()
  val inputIter = child.outputSet.toIterator
  var i = 0
  while (inputIter. hasNext) (
  val inputattr = inputiter.next ()
  sourceTypes (i) = sparkTypeToOmniType (inputattr.dataType, inputattr.metadata)
  attrMap += (inputAttr.name -> i)
  i += 1
  1/ partition column parameters
  // sort column parameters
  i=o
  for (sortAttr <- orderSpec)
  if (attrMap.contains (sortattr.child.asInstanceOf [AttributeReference]. name)) (
  sortCols (i) = attrMap (sortAttr.child.asInstanceof [AttributeReference].name)
  ascendings (i) = sortAttr.isAscending matchi
  case true => 1
  case_=>o
  nullFirsts (i) = sortAttr.nullordering.sql match 1
  case "NULLS LAST" =>o
  case_=> 1
  ) else
  throw new UnsupportedoperationException (s"Unsupported sort col not in inputset: S(sortAttr
  .nodeName ) ")
i+=1
  i=0
  1/ only window column no need to as output
  val outputcols = new Array [Int] (child.output.size) //0, 1
  for (outputAttr <- child.output) (
  if (attrMap.contains (outputattr.name))
  outputcols (i) = attrMap.get (outputAttr.name).get
  ) else
  throw new UnsupportedoperationException (s"output col not in input cols: S(outputAttr. name] ")
  i+=1
  1/ partitionSpec: Seg(Expression]
  i=0
  for (partitionattr <- partitionSpec)
  if (attrMap.contains (partitionAttr.asInstanceOf [AttributeReference] .name))
  omminPartitionchannels (i) = attrMap (partitionAttr.asInstanceof [AttributeReference] .name) i=0
  for (partitionattr <- partitionspec)
  if (attrMap.contains (partitionattr.asInstanceOf [AttributeReference] .name))i
  omminPartitionChannels (i) = attrMap (partitionattr.asinstanceOf [AttributeReference] .name)
  ) else (
  throw new UnsupportedoperationException (s"output col not in input cols: S(partitionattr)")
  02 Α 20 Α7ΚΙΑ
  +=-1
  var windowExpressionwithProject = false
  i=0
  windowExpression. foreach ( x =>
  x.foreach
  case e@windowExpression (function, spec) =>
  windowFunRetType (0) = sparkTypeToomniType (function.dataType)
  function match
  //-AggregatewindowFunction
  case winfunc: windowFunction =>
  windowFunType (0) = toomniWindowFunType(winfunc)
  windowArgkeys = winfunc.children.map (
  exp => rewriteToOmniJsonExpressionLiteral (exp, omniattrExpsIdMap)).toArray
  windowArgKeysForskip = winfunc.children.map(
  exp => rewriteToOmniExpressionLiteral(exp, omniAttrExpsIdMap)).toArray
  // AggregateExpression
  case aggeAggregateExpression (aggFunc, ___,_) =>
  windowFunType (0) = toomniAggFunType (agg)
  windowArgKeys = aggFunc. children.map (
  exp => rewritetoomniJsonexpressionLiteral (exp, omniattrExpsIdMap)).toArray
  windowArgKeysForSkip = aggFunc.children.map
  exp => rewriteToOmniExpressionLiteral (exp, omniAttrExpsIdMap)). toArray
  S(function) ")
case=> throw new UnsupportedOperationException (s"Unsupported window function:
  if (spec.frameSpecification.isInstanceof [SpecifiedwindowFrame]) 1
  var winFram = spec.frameSpecification.asInstanceof [SpecifiedwindowFrame]
  if (winFram.lower != UnboundedPreceding)
  throw new UnsupportedOperationException (s"Unsupported Specified frame_start: S(winFram
  lower)")
upper)") else if (winFram.upper != UnboundedFollowing && winFram. upper != CurrentRow) 1
throw new UnsupportedoperationException (s"Unsupported specified frame_end: S(winFram
  case=>
  windowExpressionwithProject = true
  val skipColumns = windowargKeysForskip.count (x => !x.startswith ("#"))
  val winExpressions: Seg [Expression] = windowFrameExpressionFactoryPairs. flatMap (_._1)
  val winExpToReferences = winExpressions.zipwithIndex.map I case (e, i) =>
  // Results of window expressions will be on the right side of child's output
  AttributeReference (String.valueof(child.output.size + i), e.dataType, e.nullable) ().toAttribute
  val winExpToReferencesMap = winExpressions.zip (winExpToReferences).toMap
  val patchedwindowExpression = windowExpression.map (_.transform (winExpToReferencesMap))
  val windowExpressionWithProjectConstant = windowExpressionWithProject
  child.executeColumnar () .mapPartitionsWithIndexInternal ( (index, iter) =>
  val startCodegen = System. nanoTime ()
  val windowOperatorFactory = new OmniwindowwithExproperatorFactory (sourcetypes, outputcols,
  windowFunType, omminPartitionChannels, preGroupedChannels, sortcols, ascendings,
  nullFirsts, preSortedChannelPrefix = 0, expectedPositions = 10000, windowArgkeys, windowFunRetType, new
  OperatorConfig (IS_ENABLE_JIT, IS_SKIP_VERIFY_EXP))
  val windowoperator = windowoperatorFactory.createoperator
  omnicodegentime += NANOSECONDS. toMillis (System. nanoTime () - startCodegen)
  while (iter.hasNext) (
  val batch = iter.next ()
  val input = transColBatchToomnivecs (batch)
  val vecBatch = new VecBatch (input, batch. numRows ())
  val start Input = System. nanoTime ()
  windowOperator.addInput (vecBatch)
  addInputTime += NANOSECONDS.toMillis (System. nanoTime() - startInput)
  numInputvecBatchs += 1
  numInputRows += batch. numRows () val sourcesize = sourceTypes.length
  var omniwindowResultIter = new Iterator [ColumnarBatch]
  override def hasNext: Boolean = f
  val startGetOp: Long = System. nanoTime ()
  var hasNext = results.hasNext
  getoutputTime += NANOSECONDS. toMillis (System. nanoTime () - startGetOp)
  hasNext.
  override def next (): ColumnarBatch = [
  val startGetop = System. nanoTime ()
  val vecBatch = results.next ()
  getoutputTime += NANOSECONDS.toMillis (System. nanoTime ()- startGetOp)
  val vectors: Seg[OmniColumnVector] = OmniColumnVector.allocateColumns (
  vecBatch.getRowCount, windowResultSchema, initVec = false)
  vectors.zipwithIndex.foreach ( case (vector, i) =>
  vector.reset ()
  if (i <= sourcesize - 1) (
  vector.setvec (vecBatch.getvectors () (i))
  ) else
  vector, setVec (vecBatch.getvectors () (i + skipWindowRstExpVeccnt))
  numOutputRows += vecBatch.getRowcount
  numOutputvecBatchs +=1
  vecBatch.close ()
  new ColumnarBatch (vectors.toArray, vecBatch.getRowCount)
  if (windowExpressionwithProjectconstant)
  val finalout = child.output t+ winExpToReferences
  val projectInputTypes = finalOut.map (
  exp => sparkTypeToomni Type (exp.datatype, exp.metadata)).toArray
  val projectExpressions = (child.output ++ patchedWindowExpression) .map(
  exp => rewriteToOmniJsonExpressionLiteral (exp, getExprIdMap (finalOut))).toArray
  dealPartitionData ( numOutputRows = null, numOutputVecBatchs = null, addInputTime, omnicodegenTime, getoutputTime, projectInputTypes, projectExpressions, omniWindowResultiter, this. schema)
  I else
  omniwindowResultIter