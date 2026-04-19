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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

// 下拉菜单类，用于显示一个按钮，点击后弹出一个菜单供用户选择。
public class DropdownMenu {
    private Button mButton;
    private PopupMenu mPopupMenu;
    private Menu mMenu;

    // 构造函数，传入上下文、按钮和菜单资源ID，初始化下拉菜单。
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    // 设置菜单项点击监听器，当用户点击菜单项时会调用这个方法。
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }
    // 查找菜单项的方法，根据菜单项ID查找并返回相应的菜单项。
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }
    // 设置按钮标题的方法，将指定的标题设置到按钮上。
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
