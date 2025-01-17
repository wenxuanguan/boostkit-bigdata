/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2021. All rights reserved.
 */
package com.huawei.boostkit.spark.jni;

import nova.hetu.omniruntime.type.DataType;
import nova.hetu.omniruntime.type.Decimal64DataType;
import nova.hetu.omniruntime.type.Decimal128DataType;
import nova.hetu.omniruntime.vector.IntVec;
import nova.hetu.omniruntime.vector.LongVec;
import nova.hetu.omniruntime.vector.VarcharVec;
import nova.hetu.omniruntime.vector.Decimal128Vec;
import nova.hetu.omniruntime.vector.Vec;

import org.apache.hadoop.hive.ql.io.sarg.ExpressionTree;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.orc.OrcFile.ReaderOptions;
import org.apache.orc.Reader.Options;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.orc.TypeDescription;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class OrcColumnarBatchJniReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrcColumnarBatchJniReader.class);

    public long reader;
    public long recordReader;
    public long batchReader;
    public int[] colsToGet;
    public int realColsCnt;

    public OrcColumnarBatchJniReader() {
        NativeLoader.getInstance();
    }

    public JSONObject getSubJson(ExpressionTree etNode) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", etNode.getOperator().ordinal());
        if (etNode.getOperator().toString().equals("LEAF")) {
            jsonObject.put("leaf", etNode.toString());
            return jsonObject;
        }
        ArrayList<JSONObject> child = new ArrayList<JSONObject>();
        for (ExpressionTree childNode : etNode.getChildren()) {
            JSONObject rtnJson = getSubJson(childNode);
            child.add(rtnJson);
        }
        jsonObject.put("child", child);
        return jsonObject;
    }

    public JSONObject getLeavesJson(List<PredicateLeaf> leaves, TypeDescription schema) {
        JSONObject jsonObjectList = new JSONObject();
        for (int i = 0; i < leaves.size(); i++) {
            PredicateLeaf pl = leaves.get(i);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op", pl.getOperator().ordinal());
            jsonObject.put("name", pl.getColumnName());
            jsonObject.put("type", pl.getType().ordinal());
            if (pl.getLiteral() != null) {
                if (pl.getType() == PredicateLeaf.Type.DATE) {
                    jsonObject.put("literal", ((int)Math.ceil(((Date)pl.getLiteral()).getTime()* 1.0/3600/24/1000)) + "");
                } else if (pl.getType() == PredicateLeaf.Type.DECIMAL) {
                    int decimalP = schema.findSubtype(pl.getColumnName()).getPrecision();
                    int decimalS = schema.findSubtype(pl.getColumnName()).getScale();
                    jsonObject.put("literal", pl.getLiteral().toString() + " " + decimalP + " " + decimalS);
                } else {
                    jsonObject.put("literal", pl.getLiteral().toString());
                }
            } else {
                jsonObject.put("literal", "");
            }
            if ((pl.getLiteralList() != null) && (pl.getLiteralList().size() != 0)){
                List<String> lst = new ArrayList<String>();
                for (Object ob : pl.getLiteralList()) {
                    if (pl.getType() == PredicateLeaf.Type.DECIMAL) {
                        int decimalP =  schema.findSubtype(pl.getColumnName()).getPrecision();
                        int decimalS =  schema.findSubtype(pl.getColumnName()).getScale();
                        lst.add(ob.toString() + " " + decimalP + " " + decimalS);
                    } else if (pl.getType() == PredicateLeaf.Type.DATE) {
                        lst.add(((int)Math.ceil(((Date)pl.getLiteral()).getTime()* 1.0/3600/24/1000)) + "");
                    } else {
                        lst.add(ob.toString());
                    }
                }
                jsonObject.put("literalList", lst);
            } else {
                jsonObject.put("literalList", new ArrayList<String>());
            }
            jsonObjectList.put("leaf-" + i, jsonObject);
        }
        return jsonObjectList;
    }

    /**
     * Init Orc reader.
     *
     * @param path split file path
     * @param options split file options
     */
    public long initializeReaderJava(String path, ReaderOptions options) {
        JSONObject job = new JSONObject();
        if (options.getOrcTail() == null) {
            job.put("serializedTail", "");
        } else {
            job.put("serializedTail", options.getOrcTail().getSerializedTail().toString());
        }
        job.put("tailLocation", 9223372036854775807L);
        reader = initializeReader(path, job);
        return reader;
    }

    /**
     * Init Orc RecordReader.
     *
     * @param options split file options
     */
    public long initializeRecordReaderJava(Options options) {
        JSONObject job = new JSONObject();
        if (options.getInclude() == null) {
            job.put("include", "");
        } else {
            job.put("include", options.getInclude().toString());
        }
        job.put("offset", options.getOffset());
        job.put("length", options.getLength());
        if (options.getSearchArgument() != null) {
            LOGGER.debug("SearchArgument:" + options.getSearchArgument().toString());
            JSONObject jsonexpressionTree = getSubJson(options.getSearchArgument().getExpression());
            job.put("expressionTree", jsonexpressionTree);
            JSONObject jsonleaves = getLeavesJson(options.getSearchArgument().getLeaves(), options.getSchema());
            job.put("leaves", jsonleaves);
        }

        List<String> allCols;
        if (options.getColumnNames() == null) {
            allCols = Arrays.asList(getAllColumnNames(reader));
        } else {
            allCols = Arrays.asList(options.getColumnNames());
        }
        ArrayList<String> colToInclu = new ArrayList<String>();
        List<String> optionField = options.getSchema().getFieldNames();
        colsToGet = new int[optionField.size()];
        realColsCnt = 0;
        for (int i = 0; i < optionField.size(); i++) {
            if (allCols.contains(optionField.get(i))) {
                colToInclu.add(optionField.get(i));
                colsToGet[i] = 0;
                realColsCnt++;
            } else {
                colsToGet[i] = -1;
            }
        }
        job.put("includedColumns", colToInclu.toArray());
        recordReader = initializeRecordReader(reader, job);
        return recordReader;
    }

    public long initBatchJava(long batchSize) {
        batchReader = initializeBatch(recordReader, batchSize);
        return 0;
    }

    public long getNumberOfRowsJava() {
        return getNumberOfRows(recordReader, batchReader);
    }

    public long getRowNumber() {
        return recordReaderGetRowNumber(recordReader);
    }

    public float getProgress() {
        return recordReaderGetProgress(recordReader);
    }

    public void close() {
        recordReaderClose(recordReader, reader, batchReader);
    }

    public void seekToRow(long rowNumber) {
        recordReaderSeekToRow(recordReader, rowNumber);
    }

    public int next(Vec[] vecList) {
        int vectorCnt = vecList.length;
        int[] typeIds = new int[realColsCnt];
        long[] vecNativeIds = new long[realColsCnt];
        long rtn = recordReaderNext(recordReader, reader, batchReader, typeIds, vecNativeIds);
        if (rtn == 0) {
            return 0;
        }
        int nativeGetId = 0;
        for (int i = 0; i < vectorCnt; i++) {
            if (colsToGet[i] != 0) {
                continue;
            }
            switch (DataType.DataTypeId.values()[typeIds[nativeGetId]]) {
                case OMNI_DATE32:
                case OMNI_INT: {
                    vecList[i] = new IntVec(vecNativeIds[nativeGetId]);
                    break;
                }
                case OMNI_LONG: {
                    vecList[i] = new LongVec(vecNativeIds[nativeGetId]);
                    break;
                }
                case OMNI_VARCHAR: {
                    vecList[i] = new VarcharVec(vecNativeIds[nativeGetId]);
                    break;
                }
                case OMNI_DECIMAL128: {
                    vecList[i] = new Decimal128Vec(vecNativeIds[nativeGetId], Decimal128DataType.DECIMAL128);
                    break;
                }
                case OMNI_DECIMAL64: {
                    vecList[i] = new LongVec(vecNativeIds[nativeGetId]);
                    break;
                }
                default: {
                    LOGGER.error("UNKNOWN TYPE ERROR IN JAVA" + DataType.DataTypeId.values()[typeIds[i]]);
                }
            }
            nativeGetId++;
        }
        return (int)rtn;
    }

    public native long initializeReader(String path, JSONObject job);

    public native long initializeRecordReader(long reader, JSONObject job);

    public native long initializeBatch(long rowReader, long batchSize);

    public native long recordReaderNext(long rowReader, long reader, long batchReader, int[] typeId, long[] vecNativeId);

    public native long recordReaderGetRowNumber(long rowReader);

    public native float recordReaderGetProgress(long rowReader);

    public native void recordReaderClose(long rowReader, long reader, long batchReader);

    public native void recordReaderSeekToRow(long rowReader, long rowNumber);

    public native String[] getAllColumnNames(long reader);

    public native long getNumberOfRows(long rowReader, long batch);
}
