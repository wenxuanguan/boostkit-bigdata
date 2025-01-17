/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2021. All rights reserved.
 */

package com.huawei.boostkit.spark;

import com.huawei.boostkit.spark.jni.SparkJniWrapper;

import java.io.File;
import nova.hetu.omniruntime.type.DataType;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_CHAR;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_DATE32;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_DATE64;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_DECIMAL128;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_DECIMAL64;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_DOUBLE;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_INT;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_LONG;
import static nova.hetu.omniruntime.type.DataType.DataTypeId.OMNI_VARCHAR;
import nova.hetu.omniruntime.type.DataTypeSerializer;
import nova.hetu.omniruntime.vector.VecBatch;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

public class ColumnShuffleCompressionTest extends ColumnShuffleTest {
    private static String shuffleDataFile = "";

    @BeforeClass
    public static void runOnceBeforeClass() {
        File folder = new File(shuffleTestDir);
        if (!folder.exists() && !folder.isDirectory()) {
            folder.mkdirs();
        }
    }

    @AfterClass
    public static void runOnceAfterClass() {
        File folder = new File(shuffleTestDir);
        if (folder.exists()) {
            deleteDir(folder);
        }
    }

    @Before
    public void runBeforeTestMethod() {

    }

    @After
    public void runAfterTestMethod() {
        File file = new File(shuffleDataFile);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void columnShuffleUncompressedTest() throws IOException {
        shuffleDataFile = shuffleTestDir + "/shuffle_dataFile_uncompressed_test";
        columnShuffleTestCompress("uncompressed", shuffleDataFile);
    }

    @Test
    public void columnShuffleSnappyCompressTest() throws IOException {
        shuffleDataFile = shuffleTestDir + "/shuffle_dataFile_snappy_test";
        columnShuffleTestCompress("snappy", shuffleDataFile);
    }

    @Test
    public void columnShuffleLz4CompressTest() throws IOException {
        shuffleDataFile = shuffleTestDir + "/shuffle_dataFile_lz4_test";
        columnShuffleTestCompress("lz4", shuffleDataFile);
    }

    @Test
    public void columnShuffleZlibCompressTest() throws IOException {
        shuffleDataFile = shuffleTestDir + "/shuffle_dataFile_zlib_test";
        columnShuffleTestCompress("zlib", shuffleDataFile);
    }

    public void columnShuffleTestCompress(String compressType, String dataFile) throws IOException {
        DataType.DataTypeId[] idTypes = {OMNI_INT, OMNI_LONG, OMNI_DOUBLE, OMNI_VARCHAR, OMNI_CHAR,
                OMNI_DATE32, OMNI_DATE64, OMNI_DECIMAL64, OMNI_DECIMAL128};
        DataType[] types = dataTypeId2DataType(idTypes);
        String inputType = DataTypeSerializer.serialize(types);
        SparkJniWrapper jniWrapper = new SparkJniWrapper();
        int partitionNum = 4;
        long splitterId = jniWrapper.nativeMake(
                "hash",
                partitionNum,
                inputType,
                types.length,
                1024,    //shuffle value_buffer init size
                compressType,
                dataFile,
                0,
                shuffleTestDir,
                64 * 1024,
                4096,
                1024 * 1024 * 1024);
        for (int i = 0; i < 999; i++) {
            VecBatch vecBatchTmp = buildVecBatch(idTypes, 1000, partitionNum, true, true);
            jniWrapper.split(splitterId, vecBatchTmp.getNativeVectorBatch());
        }
        jniWrapper.stop(splitterId);
        jniWrapper.close(splitterId);
    }

}
