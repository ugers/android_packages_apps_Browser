/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.browser;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.webkit.WebView;

import com.android.browser.provider.BrowserProvider2.Snapshots;

import java.io.ByteArrayInputStream;


public class SnapshotTab extends Tab {

    private long mSnapshotId;
    private LoadData mLoadTask;
    private WebViewFactory mWebViewFactory;
    // TODO: Support non-persistent webview's on phone
    private boolean mPersistentWebview;
    private int mBackgroundColor;

    public SnapshotTab(WebViewController wvcontroller, long snapshotId) {
        super(wvcontroller, null);
        mSnapshotId = snapshotId;
        mWebViewFactory = mWebViewController.getWebViewFactory();
        mPersistentWebview = !BrowserActivity.isTablet(wvcontroller.getActivity());
        if (mPersistentWebview) {
            WebView web = mWebViewFactory.createWebView(false);
            setWebView(web);
        }
        loadData();
    }

    @Override
    void putInForeground() {
        if (getWebView() == null) {
            WebView web = mWebViewFactory.createWebView(false);
            if (mBackgroundColor != 0) {
                web.setBackgroundColor(mBackgroundColor);
            }
            setWebView(web);
            loadData();
        }
        super.putInForeground();
    }

    @Override
    void putInBackground() {
        if (getWebView() == null) return;
        super.putInBackground();
        if (!mPersistentWebview) {
            super.destroy();
        }
    }

    void loadData() {
        if (mLoadTask == null) {
            mLoadTask = new LoadData(this, mActivity.getContentResolver());
            mLoadTask.execute();
        }
    }

    @Override
    void addChildTab(Tab child) {
        throw new IllegalStateException("Snapshot tabs cannot have child tabs!");
    }

    @Override
    public boolean isSnapshot() {
        return true;
    }

    public long getSnapshotId() {
        return mSnapshotId;
    }

    @Override
    public ContentValues createSnapshotValues() {
        return null;
    }

    @Override
    boolean saveState() {
        return false;
    }

    static class LoadData extends AsyncTask<Void, Void, Cursor> {

        static final String[] PROJECTION = new String[] {
            Snapshots._ID, // 0
            Snapshots.TITLE, // 1
            Snapshots.URL, // 2
            Snapshots.FAVICON, // 3
            Snapshots.VIEWSTATE, // 4
            Snapshots.BACKGROUND, // 5
        };

        private SnapshotTab mTab;
        private ContentResolver mContentResolver;

        public LoadData(SnapshotTab t, ContentResolver cr) {
            mTab = t;
            mContentResolver = cr;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            long id = mTab.mSnapshotId;
            Uri uri = ContentUris.withAppendedId(Snapshots.CONTENT_URI, id);
            return mContentResolver.query(uri, PROJECTION, null, null, null);
        }

        @Override
        protected void onPostExecute(Cursor result) {
            try {
                if (result.moveToFirst()) {
                    mTab.mCurrentState.mTitle = result.getString(1);
                    mTab.mCurrentState.mUrl = result.getString(2);
                    byte[] favicon = result.getBlob(3);
                    if (favicon != null) {
                        mTab.mCurrentState.mFavicon = BitmapFactory
                                .decodeByteArray(favicon, 0, favicon.length);
                    }
                    WebView web = mTab.getWebView();
                    if (web != null) {
                        byte[] data = result.getBlob(4);
                        ByteArrayInputStream stream = new ByteArrayInputStream(data);
                        web.loadViewState(stream);
                    }
                    mTab.mBackgroundColor = result.getInt(5);
                    mTab.mWebViewController.onPageFinished(mTab);
                }
            } finally {
                if (result != null) {
                    result.close();
                }
                mTab.mLoadTask = null;
            }
        }

    }
}