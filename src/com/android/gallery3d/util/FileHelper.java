package com.android.gallery3d.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.BucketHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Title:TODO
 * Description:TODO
 * Copyright:Copyright (c) 2015
 * Company:Wingtech
 * Author:lijin
 * Date:17-4-18
 * Version 1.0
 */

public class FileHelper {

    public static final int ERROR_FILE_NAME_IS_NOT_EMPTY = 0;
    public static final int ERROR_FILE_NAME_ALREADY_EXISTS = 1;
    public static final int ERROR_FILE_NAME_CONTAINS_ILLEGAL_CHAR = 2;
    public static final int ERROR_FILE_NAME_TOO_LONG = 3;
    public static final int ERROR_FILE_NAME_UNKOWN = 4;
    public static final int CREATE_ALBUM_SUCC = 5;

    private static final String TAG = "FileHelper";
    private static final String REGEX = "[a-zA-Z0-9\\s\u4e00-\u9fa5]+";
    private FileListener mFileListener;
    private Context mContext;


    public FileHelper(Context context) {
        this.mContext = context;
    }

    private int retriveVideoDurationMs(String path) {
        int durationMs = 0;
        // Calculate the duration of the destination file.
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (duration != null) {
            durationMs = Integer.parseInt(duration);
        }
        retriever.release();
        return durationMs;
    }

    /**
     * 新建目录
     *
     * @param folderPath String 如 c:/fqf
     * @return boolean
     */
    public void newFolder(String folderPath) {
        try {
            String filePath = folderPath;
            filePath = filePath.toString();
            File myFilePath = new File(filePath);
            if (!myFilePath.exists()) {
                myFilePath.mkdir();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.d(TAG, "newFolder - err: " + e.getMessage());
        }
    }

    /**
     * 新建文件
     *
     * @param filePathAndName String 文件路径及名称 如c:/fqf.txt
     * @param fileContent     String 文件内容
     * @return boolean
     */
    public void newFile(String filePathAndName, String fileContent) {
        FileWriter resultFile = null;
        PrintWriter myFile = null;
        try {
            String filePath = filePathAndName;
            filePath = filePath.toString();
            File myFilePath = new File(filePath);
            if (!myFilePath.exists()) {
                myFilePath.createNewFile();
            }
            resultFile = new FileWriter(myFilePath);
            myFile = new PrintWriter(resultFile);
            String strContent = fileContent;
            myFile.println(strContent);
        } catch (Exception e) {
            e.printStackTrace();
            Logger.d(TAG, "newFile - err: " + e.getMessage());
        } finally {
            Utils.closeSilently(resultFile);
            Utils.closeSilently(myFile);
        }
    }

    /**
     * 删除文件
     *
     * @param filePathAndName String 文件路径及名称 如c:/fqf.txt
     * @return boolean
     */
    public void delFile(String filePathAndName) {
        try {
            String filePath = filePathAndName;
            filePath = filePath.toString();
            File myDelFile = new File(filePath);
            myDelFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.d(TAG, "delFile - err: " + e.getMessage());
        }
    }

    /**
     * 删除文件夹
     *
     * @param folderPath String 文件夹路径及名称 如c:/fqf
     * @return boolean
     */
    public void delFolder(String folderPath) {
        try {
            delAllFile(folderPath);
            String filePath = folderPath;
            filePath = filePath.toString();
            File myFilePath = new File(filePath);
            myFilePath.delete();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.d(TAG, "delFolder - err: " + e.getMessage());
        }
    }

    /**
     * 删除文件夹里面的所有文件
     *
     * @param path String 文件夹路径 如 c:/fqf
     */
    public void delAllFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        if (!file.isDirectory()) {
            return;
        }
        String[] tempList = file.list();
        File temp = null;
        for (int i = 0; i < tempList.length; i++) {
            if (path.endsWith(File.separator)) {
                temp = new File(path + tempList[i]);
            } else {
                temp = new File(path + File.separator + tempList[i]);
            }
            if (temp.isFile()) {
                temp.delete();
            }
            if (temp.isDirectory()) {
                delAllFile(path + File.separator + tempList[i]);
                delFolder(path + File.separator + tempList[i]);
            }
        }
    }

    /**
     * 复制单个文件
     *
     * @param oldPath String 原文件路径 如：c:/fqf.txt
     * @param newPath String 复制后路径 如：f:/fqf.txt
     * @return boolean
     */
    public void copyFile(String oldPath, String newPath) {
        int bytesum = 0;
        int byteread = 0;
        InputStream inStream = null;
        FileOutputStream fs = null;
        try {
            File oldfile = new File(oldPath);
            if (oldfile.exists()) {
                inStream = new FileInputStream(oldPath);
                fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                while ((byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread;
                    fs.write(buffer, 0, byteread);
                }
            } else {
                Logger.d(TAG, "copyFile - file is't exits: " + oldPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.d(TAG, "copyFile - err: " + e.getMessage());
        } finally {
            Utils.closeSilently(inStream);
            Utils.closeSilently(fs);
        }
    }

    /**
     * 复制整个文件夹内容
     *
     * @param oldPath String 原文件路径 如：c:/fqf
     * @param newPath String 复制后路径 如：f:/fqf/ff
     * @return boolean
     */
    public void copyFolder(String oldPath, String newPath) {
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            (new File(newPath)).mkdirs();
            File a = new File(oldPath);
            String[] file = a.list();
            File temp = null;
            for (int i = 0; i < file.length; i++) {
                if (oldPath.endsWith(File.separator)) {
                    temp = new File(oldPath + file[i]);
                } else {
                    temp = new File(oldPath + File.separator + file[i]);
                }
                if (temp.isFile()) {
                    input = new FileInputStream(temp);
                    output = new FileOutputStream(newPath + File.separator + (temp.getName()).toString());
                    byte[] b = new byte[1024 * 5];
                    int len;
                    while ((len = input.read(b)) != -1) {
                        output.write(b, 0, len);
                    }
                }
                if (temp.isDirectory()) {
                    copyFolder(oldPath + File.separator + file[i], newPath + File.separator + file[i]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.d(TAG, "copyFolder - err: " + e.getMessage());
        } finally {
            Utils.closeSilently(input);
            Utils.closeSilently(output);
        }
    }

    /**
     * 移动文件到指定目录
     *
     * @param oldPath String 如：c:/fqf.txt
     * @param newPath String 如：d:/fqf.txt
     */
    public void moveFile(String oldPath, String newPath) {
        copyFile(oldPath, newPath);
        delFile(oldPath);
    }

    /**
     * 移动文件到指定目录
     *
     * @param oldPath String 如：c:/fqf.txt
     * @param newPath String 如：d:/
     */
    public void moveFolder(String oldPath, String newPath) {
        copyFolder(oldPath, newPath);
        delFolder(oldPath);
    }

    /**
     * Insert the content (saved file) with proper pic properties.
     */
    private Uri insertDefaultPicture(File file, ContentResolver contentResolver) {
        long nowInMs = System.currentTimeMillis();
        long nowInSec = nowInMs / 1000;
        final ContentValues values = new ContentValues(8);
        values.put(MediaStore.Images.Media.TITLE, GalleryUtils.EXTRA_DEFAULT_FILE_NAME);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, GalleryUtils.EXTRA_DEFAULT_FILE_NAME);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_TAKEN, nowInMs);
        values.put(MediaStore.Images.Media.DATE_MODIFIED, nowInSec);
        values.put(MediaStore.Images.Media.DATE_ADDED, nowInSec);
        values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
        values.put(MediaStore.Images.Media.SIZE, file.length());
        //int durationMs = retriveVideoDurationMs(file.getPath());
        //values.put(MediaStore.Images.Media.DURATION, durationMs);

        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    public void setCreateListener(FileListener listener) {
        this.mFileListener = listener;
    }

    /**
     * put default pic into folder
     *
     * @param folderName
     */
    public void putPictureIntoFolder(String folderName) {
        File mNewFolder = new File(MediaSetUtils.DIRECTORY_PICTURES + File.separator + folderName);
        if ((!mNewFolder.exists() && mNewFolder.mkdirs()) ||
                BucketHelper.isEmptyAlbum(mContext.getContentResolver(), mNewFolder.getPath())) {
            File defaultPic = new File(mNewFolder, GalleryUtils.EXTRA_DEFAULT_FILE_NAME);
            FileOutputStream fileOutputStream = null;
            try {
                defaultPic.createNewFile();
                fileOutputStream = new FileOutputStream(defaultPic);
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.empty).compress(Bitmap.CompressFormat.JPEG, 100,
                        fileOutputStream);

                Uri mU = insertDefaultPicture(defaultPic, mContext.getContentResolver());
                if (mU != null) {
                    mFileListener.onCreateResult(CREATE_ALBUM_SUCC);
                } else {
                    mFileListener.onCreateResult(ERROR_FILE_NAME_UNKOWN);
                }
            } catch (Exception ex) {
                mFileListener.onCreateResult(ERROR_FILE_NAME_UNKOWN);
                Logger.e(TAG, "putPictureIntoFolder err: " + ex.getMessage());
            } finally {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (IOException ex) {
                    mFileListener.onCreateResult(ERROR_FILE_NAME_UNKOWN);
                    Logger.e(TAG, "putPictureIntoFolder err: " + ex.getMessage());
                }
            }
        } else {
            mFileListener.onCreateResult(ERROR_FILE_NAME_ALREADY_EXISTS);
        }
    }

    /**
     * put target pic into folder
     *
     * @param folderName
     */
    public void putTargetPicIntoFolder(Uri fromUri, String folderName) {
        try {
            File mNewFolder = new File(MediaSetUtils.DIRECTORY_PICTURES + File.separator + folderName);
            if ((!mNewFolder.exists() && mNewFolder.mkdirs()) ||
                    BucketHelper.isEmptyAlbum(mContext.getContentResolver(), mNewFolder.getPath())) {

                String fromPath = BucketHelper.getPathByUri(mContext.getContentResolver(), fromUri);
                String fromName = fromPath.substring(fromPath.lastIndexOf(File.separator), fromPath.length());
                File targetFile = new File(mNewFolder, fromName);

                moveFile(fromPath, targetFile.getPath());
                Logger.d(TAG, "putTargetPicIntoFolder - fromPath: " + fromPath + " targetPath: " + targetFile.getPath());

                String[] fileArrays = new String[]{fromPath, targetFile.getPath()};

                MediaScannerConnection.scanFile(mContext, fileArrays, null, null);

                mFileListener.onCreateResult(CREATE_ALBUM_SUCC);
            } else {
                mFileListener.onCreateResult(ERROR_FILE_NAME_ALREADY_EXISTS);
            }
        } catch (Exception ex) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_UNKOWN);
            Logger.e(TAG, "putPictureIntoFolder err: " + ex.getMessage());
        }
    }

    /**
     * Validata name for create album
     *
     * @param name
     * @return
     */
    public boolean validateNewFileName(AbstractGalleryActivity mActivity, String name) {
        if (TextUtils.isEmpty(name)) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_IS_NOT_EMPTY);
            return false;
        }
        int mLength = name.length();
        if (mLength > 20) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_TOO_LONG);
            return false;
        }
        Pattern pattern = Pattern.compile(REGEX);
        Matcher matcher = pattern.matcher(name);
        if (!matcher.matches()) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_CONTAINS_ILLEGAL_CHAR);
            return false;
        }
        File mNewFolder = new File(MediaSetUtils.DIRECTORY_PICTURES + File.separator + name);
        if ((mNewFolder.exists() && !BucketHelper.isEmptyAlbum(mContext.getContentResolver(), mNewFolder.getPath()))
                || mActivity.getAlbumsManager().exists(name)) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_ALREADY_EXISTS);
            return false;
        }
        if (MediaSetUtils.isSysAlbumName(mActivity.getResources(), name)) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_ALREADY_EXISTS);
            return false;
        }

        return true;
    }

    /**
     * Validata name for rename
     *
     * @param name
     * @return
     */
    public boolean validateFileName(AbstractGalleryActivity mActivity, String name) {
        if (TextUtils.isEmpty(name)) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_IS_NOT_EMPTY);
            return false;
        }
        int mLength = name.length();
        if (mLength > 20) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_TOO_LONG);
            return false;
        }
        Pattern pattern = Pattern.compile(REGEX);
        Matcher matcher = pattern.matcher(name);
        if (!matcher.matches()) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_CONTAINS_ILLEGAL_CHAR);
            return false;
        }

        if (MediaSetUtils.isSysAlbumName(mActivity.getResources(), name)) {
            mFileListener.onCreateResult(ERROR_FILE_NAME_ALREADY_EXISTS);
            return false;
        }
        return true;
    }

    public interface FileListener {
        void onCreateResult(int result);
    }
}
