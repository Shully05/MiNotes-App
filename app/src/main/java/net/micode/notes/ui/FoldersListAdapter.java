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

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;


// 文件夹列表适配器，用于显示文件夹列表
public class FoldersListAdapter extends CursorAdapter {
    // 定义查询文件夹数据的投影，包括ID和SNIPPET列。
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET
    };

    // 定义列索引常量，用于在Cursor中访问相应的列数据。
    public static final int ID_COLUMN   = 0;
    public static final int NAME_COLUMN = 1;

    // 构造函数，传入上下文和Cursor对象，并调用父类的构造函数进行初始化。
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    // 创建新的视图，用于显示文件夹列表项。当CursorAdapter需要一个新的视图来显示数据时，会调用这个方法。
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    // 绑定数据到视图，将Cursor中的数据绑定到新创建的视图上。当CursorAdapter需要将数据绑定到视图时，会调用这个方法。
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }
    // 获取文件夹名称的方法，根据Cursor中的数据获取文件夹名称，如果ID列的值等于Notes.ID_ROOT_FOLDER，则返回一个特定的字符串，否则返回NAME_COLUMN列的值。 
    public String getFolderName(Context context, int position) {
        // 从Cursor中获取指定位置的数据，并根据ID列的值返回相应的文件夹名称。
        Cursor cursor = (Cursor) getItem(position);
        // 如果ID列的值等于Notes.ID_ROOT_FOLDER，则返回一个特定的字符串，否则返回NAME_COLUMN列的值。
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    // 内部类，用于显示文件夹列表项。
    private class FolderListItem extends LinearLayout {
        // 定义视图组件，用于显示文件夹名称。
        private TextView mName;

        // 构造函数，传入上下文，并初始化视图。
        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        // 绑定数据到视图，将文件夹名称设置到文本视图中。
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
