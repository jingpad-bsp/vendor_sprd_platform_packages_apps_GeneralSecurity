package com.sprd.generalsecurity.storage;

import java.text.Collator;

/**
 * Created by SPREADTRUM\bo.yan on 17-5-15.
 */

public class FileDetailModel implements Comparable<FileDetailModel> {
    private String filePath;
    private long fileSize;


    public FileDetailModel() {
    }

    public FileDetailModel(String path, long size) {
        this.filePath = path;
        this.fileSize = size;
    }

    public void setFilePath(String path) {
        this.filePath = path;
    }

    public void setFileSize(long size) {
        this.fileSize = size;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    /**
     * UNISOC: Bug771467,850084 display large file detail list in the extension list
     * @{
     */
    @Override
    public int compareTo(FileDetailModel another) {
        long result;
        result = fileSize - another.fileSize;
        if (result == 0) {
            return Collator.getInstance().compare(filePath, another.filePath);
        } else if (result > 0) {
            return 1;
        } else {
            return -1;
        }
    }
    /**
     * @}
     */
}
