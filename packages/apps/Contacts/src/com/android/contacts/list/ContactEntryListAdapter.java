/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import com.android.contacts.ContactPhotoManager;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;

import com.android.contacts.widget.IndexerListAdapter;
import com.android.contacts.widget.TextWithHighlightingFactory;
import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.simcontact.SIMInfoWrapper;
import com.mediatek.contacts.util.OperatorUtils;

import android.content.Context;
import android.content.CursorLoader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.HashSet;
//The following lines are provided and maintained by Mediatek inc.
import com.android.internal.telephony.ITelephony;
import android.os.ServiceManager;
import android.provider.Telephony.SIMInfo;

//The following lines are provided and maintained by Mediatek inc.
/**
 * Common base class for various contact-related lists, e.g. contact list, phone number list
 * etc.
 */
public abstract class ContactEntryListAdapter extends IndexerListAdapter {

    private static final String TAG = "ContactEntryListAdapter";

    /**
     * Indicates whether the {@link Directory#LOCAL_INVISIBLE} directory should
     * be included in the search.
     */
    private static final boolean LOCAL_INVISIBLE_DIRECTORY_ENABLED = false;

    /**
     * The animation is used here to allocate animated name text views.
     */
    private TextWithHighlightingFactory mTextWithHighlightingFactory;
    private int mDisplayOrder;
    private int mSortOrder;
    private boolean mNameHighlightingEnabled;

    private boolean mDisplayPhotos;
    private boolean mQuickContactEnabled;

    /**
     * indicates if contact queries include profile
     */
    private boolean mIncludeProfile;

    /**
     * indicates if query results includes a profile
     */
    private boolean mProfileExists;

    private ContactPhotoManager mPhotoLoader;

    private String mQueryString;
    private char[] mUpperCaseQueryString;
    private boolean mSearchMode;
    private int mDirectorySearchMode;
    private int mDirectoryResultLimit = Integer.MAX_VALUE;

    private boolean mLoading = true;
    private boolean mEmptyListEnabled = true;

    private boolean mSelectionVisible;

    private ContactListFilter mFilter;
    private String mContactsCount = "";
    private boolean mDarkTheme = false;
  
    protected String mTempFlag = null;
    
    public ContactEntryListAdapter(Context context) {
        super(context);
        addPartitions();
    }

    @Override
    protected View createPinnedSectionHeaderView(Context context, ViewGroup parent) {
        return new ContactListPinnedHeaderView(context, null);
    }

    @Override
    protected void setPinnedSectionTitle(View pinnedHeaderView, String title) {
        ((ContactListPinnedHeaderView)pinnedHeaderView).setSectionHeader(title);
    }

    @Override
    protected void setPinnedHeaderContactsCount(View header) {
        // Update the header with the contacts count only if a profile header exists
        // otherwise, the contacts count are shown in the empty profile header view
        if (mProfileExists) {
            ((ContactListPinnedHeaderView)header).setCountView(mContactsCount);
        } else {
            clearPinnedHeaderContactsCount(header);
        }
    }

    @Override
    protected void clearPinnedHeaderContactsCount(View header) {
        ((ContactListPinnedHeaderView)header).setCountView(null);
    }

    protected void addPartitions() {
        addPartition(createDefaultDirectoryPartition());
    }

    protected DirectoryPartition createDefaultDirectoryPartition() {
        DirectoryPartition partition = new DirectoryPartition(true, true);
        partition.setDirectoryId(Directory.DEFAULT);
        partition.setDirectoryType(getContext().getString(R.string.contactsList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(true);
        return partition;
    }

    private int getPartitionByDirectoryId(long id) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                if (((DirectoryPartition)partition).getDirectoryId() == id) {
                    return i;
                }
            }
        }
        return -1;
    }

    public abstract String getContactDisplayName(int position);
    public abstract void configureLoader(CursorLoader loader, long directoryId);

    /**
     * Marks all partitions as "loading"
     */
    public void onDataReload() {
        boolean notify = false;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (!directoryPartition.isLoading()) {
                    notify = true;
                }
                directoryPartition.setStatus(DirectoryPartition.STATUS_NOT_LOADED);
            }
        }
        if (notify) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void clearPartitions() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                directoryPartition.setStatus(DirectoryPartition.STATUS_NOT_LOADED);
            }
        }
        super.clearPartitions();
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString) {
        mQueryString = queryString;
        if (TextUtils.isEmpty(queryString)) {
            mUpperCaseQueryString = null;
        } else {
            mUpperCaseQueryString = queryString.toUpperCase().toCharArray();
        }
    }

    public char[] getUpperCaseQueryString() {
        return mUpperCaseQueryString;
    }

    public int getDirectorySearchMode() {
        return mDirectorySearchMode;
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    public int getDirectoryResultLimit() {
        return mDirectoryResultLimit;
    }

    public void setDirectoryResultLimit(int limit) {
        this.mDirectoryResultLimit = limit;
    }

    public int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
    }

    public void setPhotoLoader(ContactPhotoManager photoLoader) {
        mPhotoLoader = photoLoader;
    }

    protected ContactPhotoManager getPhotoLoader() {
        return mPhotoLoader;
    }

    public boolean getDisplayPhotos() {
        return mDisplayPhotos;
    }

    public void setDisplayPhotos(boolean displayPhotos) {
        mDisplayPhotos = displayPhotos;
    }

    public boolean isEmptyListEnabled() {
        return mEmptyListEnabled;
    }

    public void setEmptyListEnabled(boolean flag) {
        mEmptyListEnabled = flag;
    }

    public boolean isSelectionVisible() {
        return mSelectionVisible;
    }

    public void setSelectionVisible(boolean flag) {
        this.mSelectionVisible = flag;
    }

    public boolean isQuickContactEnabled() {
        return mQuickContactEnabled;
    }

    public void setQuickContactEnabled(boolean quickContactEnabled) {
        mQuickContactEnabled = quickContactEnabled;
    }

    public boolean shouldIncludeProfile() {
        return mIncludeProfile;
    }

    public void setIncludeProfile(boolean includeProfile) {
        mIncludeProfile = includeProfile;
    }

    public void setProfileExists(boolean exists) {
        mProfileExists = exists;
        // Stick the "ME" header for the profile
        if (exists) {
            SectionIndexer indexer = getIndexer();
            if (indexer != null) {
                ((ContactsSectionIndexer) indexer).setProfileHeader(
                        getContext().getString(R.string.user_profile_contacts_list_header));
            }
        }
    }

    public boolean hasProfile() {
        return mProfileExists;
    }

    public void setDarkTheme(boolean value) {
        mDarkTheme = value;
    }

    public void configureDirectoryLoader(DirectoryListLoader loader) {
        loader.setDirectorySearchMode(mDirectorySearchMode);
        loader.setLocalInvisibleDirectoryEnabled(LOCAL_INVISIBLE_DIRECTORY_ENABLED);
    }

    /**
     * Updates partitions according to the directory meta-data contained in the supplied
     * cursor.
     */
    public void changeDirectories(Cursor cursor) {
        if (cursor.getCount() == 0) {
            // Directory table must have at least local directory, without which this adapter will
            // enter very weird state.
            Log.e(TAG, "Directory search loader returned an empty cursor, which implies we have " +
                    "no directory entries.", new RuntimeException());
            return;
        }
        HashSet<Long> directoryIds = new HashSet<Long>();

        int idColumnIndex = cursor.getColumnIndex(Directory._ID);
        int directoryTypeColumnIndex = cursor.getColumnIndex(DirectoryListLoader.DIRECTORY_TYPE);
        int displayNameColumnIndex = cursor.getColumnIndex(Directory.DISPLAY_NAME);
        int photoSupportColumnIndex = cursor.getColumnIndex(Directory.PHOTO_SUPPORT);

        // TODO preserve the order of partition to match those of the cursor
        // Phase I: add new directories
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumnIndex);
            directoryIds.add(id);
            if (getPartitionByDirectoryId(id) == -1) {
                DirectoryPartition partition = new DirectoryPartition(false, true);
                partition.setDirectoryId(id);
                partition.setDirectoryType(cursor.getString(directoryTypeColumnIndex));
                partition.setDisplayName(cursor.getString(displayNameColumnIndex));
                int photoSupport = cursor.getInt(photoSupportColumnIndex);
                partition.setPhotoSupported(photoSupport == Directory.PHOTO_SUPPORT_THUMBNAIL_ONLY
                        || photoSupport == Directory.PHOTO_SUPPORT_FULL);
                addPartition(partition);
            }
        }

        // Phase II: remove deleted directories
        int count = getPartitionCount();
        for (int i = count; --i >= 0; ) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                long id = ((DirectoryPartition)partition).getDirectoryId();
                if (!directoryIds.contains(id)) {
                    removePartition(i);
                }
            }
        }

        invalidate();
        notifyDataSetChanged();
    }

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        if (partitionIndex >= getPartitionCount()) {
            // There is no partition for this data
            return;
        }

        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            ((DirectoryPartition)partition).setStatus(DirectoryPartition.STATUS_LOADED);
        }

        if (mDisplayPhotos && mPhotoLoader != null && isPhotoSupported(partitionIndex)) {
            mPhotoLoader.refreshCache();
        }

        super.changeCursor(partitionIndex, cursor);

        if (isSectionHeaderDisplayEnabled() && partitionIndex == getIndexedPartition()) {
            updateIndexer(cursor);
        }
    }

    public void changeCursor(Cursor cursor) {
        changeCursor(0, cursor);
    }

    /**
     * Updates the indexer, which is used to produce section headers.
     */
    public void updateIndexer(Cursor cursor) {
        if (cursor == null) {
            setIndexer(null);
            return;
        }

        Bundle bundle = cursor.getExtras();
        if (bundle.containsKey(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)) {
            String sections[] =
                    bundle.getStringArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            int counts[] = bundle.getIntArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            setIndexer(new ContactsSectionIndexer(sections, counts));
        } else {
            setIndexer(null);
        }
    }

    @Override
    public int getViewTypeCount() {
        // We need a separate view type for each item type, plus another one for
        // each type with header, plus one for "other".
        return getItemViewTypeCount() * 2 + 1;
    }

    @Override
    public int getItemViewType(int partitionIndex, int position) {
        int type = super.getItemViewType(partitionIndex, position);
        if (!isUserProfile(position)
                && isSectionHeaderDisplayEnabled()
                && partitionIndex == getIndexedPartition()) {
            Placement placement = getItemPlacementInSection(position);
            return placement.firstInSection ? type : getItemViewTypeCount() + type;
        } else {
            return type;
        }
    }

    @Override
    public boolean isEmpty() {
        // TODO
//        if (contactsListActivity.mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return true;
//        }

        if (!mEmptyListEnabled) {
            return false;
        } else if (isSearchMode()) {
            return TextUtils.isEmpty(getQueryString());
        } else if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    public boolean isLoading() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition
                    && ((DirectoryPartition) partition).isLoading()) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllPartitionsEmpty() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            if (!isPartitionEmpty(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Changes visibility parameters for the default directory partition.
     */
    public void configureDefaultPartition(boolean showIfEmpty, boolean hasHeader) {
        int defaultPartitionIndex = -1;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition &&
                    ((DirectoryPartition)partition).getDirectoryId() == Directory.DEFAULT) {
                defaultPartitionIndex = i;
                break;
            }
        }
        if (defaultPartitionIndex != -1) {
            setShowIfEmpty(defaultPartitionIndex, showIfEmpty);
            setHasHeader(defaultPartitionIndex, hasHeader);
        }
    }

    @Override
    protected View newHeaderView(Context context, int partition, Cursor cursor,
            ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.directory_header, parent, false);
    }

    @Override
    protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
        Partition partition = getPartition(partitionIndex);
        if (!(partition instanceof DirectoryPartition)) {
            return;
        }

        DirectoryPartition directoryPartition = (DirectoryPartition)partition;
        long directoryId = directoryPartition.getDirectoryId();
        TextView labelTextView = (TextView)view.findViewById(R.id.label);
        TextView displayNameTextView = (TextView)view.findViewById(R.id.display_name);
        if (directoryId == Directory.DEFAULT || directoryId == Directory.LOCAL_INVISIBLE) {
            labelTextView.setText(R.string.local_search_label);
            displayNameTextView.setText(null);
        } else {
            labelTextView.setText(R.string.directory_search_label);
            String directoryName = directoryPartition.getDisplayName();
            String displayName = !TextUtils.isEmpty(directoryName)
                    ? directoryName
                    : directoryPartition.getDirectoryType();
            displayNameTextView.setText(displayName);
        }
        //MTK_THEMEMANAGER_APP
        if (FeatureOption.MTK_THEMEMANAGER_APP) {
            Resources res = mContext.getResources();
            int textColor = res.getThemeMainColor();
            if (textColor != 0) {
                labelTextView.setTextColor(textColor);
            }
        }
        //MTK_THEMEMANAGER_APP
        TextView countText = (TextView)view.findViewById(R.id.count);
        if (directoryPartition.isLoading()) {
            countText.setText(R.string.search_results_searching);
        } else {
            int count = cursor == null ? 0 : cursor.getCount();
            if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE
                    && count >= getDirectoryResultLimit()) {
                countText.setText(mContext.getString(
                        R.string.foundTooManyContacts, getDirectoryResultLimit()));
            } else {
                countText.setText(getQuantityText(
                        count, R.string.listFoundAllContactsZero, R.plurals.searchFoundContacts));
            }
        }
    }

    /**
     * Checks whether the contact entry at the given position represents the user's profile.
     */
    protected boolean isUserProfile(int position) {
        // The profile only ever appears in the first position if it is present.  So if the position
        // is anything beyond 0, it can't be the profile.
        boolean isUserProfile = false;
        if (position == 0) {
            int partition = getPartitionForPosition(position);
            if (partition >= 0) {
                // Save the old cursor position - the call to getItem() may modify the cursor
                // position.
                int offset = getCursor(partition).getPosition();
                Cursor cursor = (Cursor) getItem(position);
                if (cursor != null) {
                    int profileColumnIndex = cursor.getColumnIndex(Contacts.IS_USER_PROFILE);
                    if (profileColumnIndex != -1) {
                        isUserProfile = cursor.getInt(profileColumnIndex) == 1;
                    }
                    // Restore the old cursor position.
                    cursor.moveToPosition(offset);
                }
            }
        }
        return isUserProfile;
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getContext().getString(zeroResourceId);
        } else {
            String format = getContext().getResources()
                    .getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }

    public boolean isPhotoSupported(int partitionIndex) {
        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            return ((DirectoryPartition) partition).isPhotoSupported();
        }
        return true;
    }

    /**
     * Returns the currently selected filter.
     */
    public ContactListFilter getFilter() {
        return mFilter;
    }

    public void setFilter(ContactListFilter filter) {
        mFilter = filter;
    }

    // TODO: move sharable logic (bindXX() methods) to here with extra arguments

    protected void bindQuickContact(final ContactListItemView view, int partitionIndex,
            Cursor cursor, int photoIdColumn, int contactIdColumn, int lookUpKeyColumn) {
        long photoId = 0;
        if (!cursor.isNull(photoIdColumn)) {
            photoId = cursor.getLong(photoIdColumn);
        }
        //The following lines are provided and maintained by Mediatek inc.
        int indicatePhoneSim = cursor.getInt(cursor
                .getColumnIndexOrThrow(Contacts.INDICATE_PHONE_SIM));
        if(indicatePhoneSim > 0){
            int isSdnContact = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.IS_SDN_CONTACT));            
            photoId = getSimType(indicatePhoneSim, isSdnContact);            
        }
        
        //The following lines are provided and maintained by Mediatek inc.
        QuickContactBadge quickContact = view.getQuickContact();
        quickContact.assignContactUri(
                getContactUri(partitionIndex, cursor, contactIdColumn, lookUpKeyColumn));
        getPhotoLoader().loadPhoto(quickContact, photoId, false, mDarkTheme);
    }

    protected Uri getContactUri(int partitionIndex, Cursor cursor,
            int contactIdColumn, int lookUpKeyColumn) {
        long contactId = cursor.getLong(contactIdColumn);
        String lookupKey = cursor.getString(lookUpKeyColumn);
        Uri uri = Contacts.getLookupUri(contactId, lookupKey);
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (directoryId != Directory.DEFAULT) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
        }
        return uri;
    }

    public void setContactsCount(String count) {
        mContactsCount = count;
    }

    public String getContactsCount() {
        return mContactsCount;
    }

    // The following lines are provided and maintained by Mediatek inc.
    ///M: #AAS# aas need mSlot to retrive labels stored in USIM
    protected int mSlot = -1;
    private SIMInfoWrapper mSimInfoWrapper;

    public long getSimType(int indicate, int isSdnContact) {
        long photoId = 0;
        if (mSimInfoWrapper == null) {
            mSimInfoWrapper = SIMInfoWrapper.getDefault();
        }

        mSlot = mSimInfoWrapper.getSimSlotById(indicate);
        int i = -1;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Log.i(TAG, "[getSimType] mSlot = " + mSlot);
            SIMInfo simInfo = mSimInfoWrapper.getSimInfoBySlot(mSlot);
            if (simInfo != null) {
                i = simInfo.mColor;
            }
            Log.i(TAG, "[getSimType] i = " + i);
            /*
             * Change Feature by Mediatek Begin.
             *   Original Android's code:
             *     xxx
             *   CR ID: ALPS00269801
             *   Descriptions:
             */
//            if (OperatorUtils.getOptrProperties().equals("OP02")) {
//                Log.i(TAG, "[getSimType] OP02 mSlot : " + mSlot);
//                if (mSlot == 0) {
//                    return -3;
//                } else {
//                    return -4;
//                }
//            } else if (i == 0) {
            /*
             * Change Feature by Mediatek End.
             */
            if (isSdnContact > 0) {
                if (i == 0) {
                    photoId = -5;
                } else if (i == 1) {
                    photoId = -6;
                } else if (i == 2) {
                    photoId = -7;
                } else if (i == 3) {
                    photoId = -8;
                } else {
                    photoId = -9;
                }
	        } else {
				if (i == 0) {
					photoId = -10;
				} else if (i == 1) {
					photoId = -11;
				} else if (i == 2) {
					photoId = -12;
				} else if (i == 3) {
					photoId = -13;
				} else {
					photoId = -1;
				}
	        }
        } else {
        	if (isSdnContact > 0) {
        		photoId = -9;
        	} else {
                photoId = -1;
        	}
        }

        Log.i(TAG, "[getSimType] photoId : " + photoId);
        return photoId;

    }
    // The following lines are provided and maintained by Mediatek inc.

    
    public void setTempFlag(final String tempFlag) {
    	this.mTempFlag = tempFlag;
    }
    
}
