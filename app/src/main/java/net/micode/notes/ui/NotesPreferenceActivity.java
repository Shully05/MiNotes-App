/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

//笔记偏好设置活动，用于显示应用程序的偏好设置。
public class NotesPreferenceActivity extends PreferenceActivity {
    
    public static final String PREFERENCE_NAME = "notes_preferences";// 偏好设置名称常量，用于存储和检索应用程序的偏好设置。
    
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";// 同步账户名称的偏好设置键常量，用于存储和检索用户选择的同步账户名称。
    
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";// 最后同步时间的偏好设置键常量，用于存储和检索上次同步的时间戳。
    
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";// 设置背景颜色的偏好设置键常量，用于存储和检索用户选择的背景颜色选项。

    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";// 同步账户的偏好设置键常量，用于存储和检索用户选择的同步账户。

    private static final String AUTHORITIES_FILTER_KEY = "authorities";// 权限过滤器的偏好设置键常量，用于存储和检索应用程序的权限信息。

    private PreferenceCategory mAccountCategory;// 同步账户的偏好设置类别，用于组织和显示与同步账户相关的偏好设置。

    private GTaskReceiver mReceiver;// GTaskReceiver实例，用于接收来自GTaskSyncService的广播，以更新UI和处理同步状态变化。

    private Account[] mOriAccounts;// 原始账户数组，用于在用户添加新账户后比较和更新同步账户信息。

    private boolean mHasAddedAccount;// 标志变量，指示用户是否已经添加了新账户，用于在onResume方法中自动设置同步账户。

    // 在活动创建时调用，初始化UI组件、注册广播接收器，并设置初始状态。
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 启用ActionBar的返回按钮，使用户能够通过点击应用程序图标返回到上一个活动。
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 从xml资源文件中加载偏好设置，并将它们添加到活动中。
        addPreferencesFromResource(R.xml.preferences);
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        // 设置初始状态，包括同步账户信息和最后同步时间，并将原始账户数组设置为null，以便在用户添加新账户后进行比较。
        mOriAccounts = null;
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    // 在活动恢复时调用，检查用户是否添加了新账户，并根据需要自动设置同步账户，同时刷新UI以反映最新的偏好设置状态。
    @Override
    protected void onResume() {
        super.onResume();

        // need to set sync account automatically if user has added a new
        // account
        // 因为在用户添加新账户后，系统会自动返回到这个活动，所以我们在这里检查用户是否添加了新账户，并根据需要自动设置同步账户。
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                for (Account accountNew : accounts) {
                    boolean found = false;
                    // 遍历原始账户数组，检查新账户是否已经存在于原始账户数组中，
                    // 如果找到了匹配的账户，则将found标志设置为true，并跳出循环。
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    // 如果在原始账户数组中没有找到新账户，则将其设置为同步账户，
                    // 并刷新UI以反映最新的同步账户信息。
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        refreshUI();
    }

    // 在活动销毁时调用，注销广播接收器。
    @Override
    protected void onDestroy() {
        // 注销广播接收器，以避免内存泄漏和不必要的资源占用。
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        // 调用父类的onDestroy方法，确保正确地销毁活动并释放资源。
        super.onDestroy();
    }

    // 创建选项菜单时调用。
    private void loadAccountPreference() {
        mAccountCategory.removeAll();

        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // the first time to set account
                        showSelectAccountAlertDialog();
                    } else {
                        // if the account has already been set, we need to promp
                        // user about the risk
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    // 加载同步按钮的状态和最后同步时间，并根据当前的同步状态更新UI组件的显示和交互逻辑。
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // set button state
        if (GTaskSyncService.isSyncing()) {
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // set last sync time
        if (GTaskSyncService.isSyncing()) {
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    // 刷新UI组件，包括同步账户信息、同步按钮状态和最后同步时间。
    private void refreshUI() {
        // 加载账户偏好设置，更新UI以反映当前的同步账户信息。
        loadAccountPreference();
        // 加载同步按钮的状态和最后同步时间，更新UI以反映当前的同步状态和上次同步的时间。
        loadSyncButton();
    }

    // 显示选择账户对话框，让用户选择同步账户。
    private void showSelectAccountAlertDialog() {
        // 创建一个AlertDialog.Builder实例，用于构建和显示选择账户的对话框。
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 设置对话框的自定义标题，包括标题文本和子标题文本，提供用户选择账户的提示信息。
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        // 设置对话框的选项列表，包括已有的账户列表，以及添加新账户的选项。
        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);

        // 获取已有的账户列表，并将它们添加到选项列表中。
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        // 将原始账户数组设置为当前的账户列表，以便在用户添加新账户后进行比较和更新同步账户信息。
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        // 如果存在账户，则将它们显示为单选项列表，让用户选择一个账户作为同步账户。
        if (accounts.length > 0) {
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            // 遍历账户列表，将每个账户的名称添加到选项列表中，并检查是否有账户与当前的同步账户匹配，以设置默认选中的项。
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            // 设置对话框的单选项列表，并为每个选项设置点击事件监听器，当用户选择一个账户时，更新同步账户信息并刷新UI。
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        // 如果不存在账户，则显示一个提示信息，让用户添加一个账户作为同步账户。
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        // 设置对话框的按钮，包括确认和取消按钮，并为每个按钮设置点击事件监听器。
        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls"
                });
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    // 显示确认对话框，让用户确认是否要更改同步账户。
    private void showChangeAccountConfirmAlertDialog() {
        // 创建一个AlertDialog.Builder实例，用于构建和显示确认对话框。
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 设置对话框的自定义标题，包括标题文本和子标题文本，提供用户更改账户的提示信息和警告信息。
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        // 设置对话框的选项列表，包括更改账户、移除账户和取消选项，让用户选择相应的操作。
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    removeSyncAccount();
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
    }

    // 获取已有的Google账户列表。
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    // 设置同步账户信息，并刷新UI以反映最新的同步账户信息。
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // clean up last sync time
            setLastSyncTime(this, 0);

            // clean up local gtask related info
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // 移除同步账户信息，并刷新UI以反映最新的同步账户信息。
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // clean up local gtask related info
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    // 获取当前的同步账户名称，以便在UI中显示和使用。
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    // 设置最后同步时间的时间戳，以便在UI中显示和使用。
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    // 获取最后同步时间的时间戳，以便在UI中显示和使用。
    public static long getLastSyncTime(Context context) {
        // 从SharedPreferences中获取最后同步时间的时间戳，如果没有设置过，则返回0。
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    // 广播接收器，用于接收同步服务的广播消息，并刷新UI以反映同步状态。
    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            // 如果同步服务正在同步，则从广播消息中获取同步进度信息，
            // 并更新UI中的同步状态文本视图，以显示当前的同步进度。
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }

        }
    }

    // 菜单项点击事件监听器，用于处理菜单项的点击事件。
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // 当用户点击ActionBar的返回按钮时，创建一个Intent对象，设置目标活动为NotesListActivity，
            // 并添加FLAG_ACTIVITY_CLEAR_TOP标志，以确保返回到上一个活动时清除中间的活动栈，然后启动目标活动。
            case android.R.id.home:
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}
