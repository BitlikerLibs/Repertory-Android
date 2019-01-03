package com.bitliker.ui.bitdialog.common.listener;

import com.bitliker.ui.bitdialog.list.BitDialogModel;

import java.io.Serializable;
import java.util.List;

/**
 * 多选回调
 */
public interface OnMultiSelectListener<T extends BitDialogModel> extends WidgetListener {

    boolean select(boolean sure, List<T> selectModels);

}
