package com.snatik.storage.app;

import android.support.design.widget.Snackbar;
import android.view.View;

/**
 * Created by sromku on June, 2017.
 */
public class UIHelper {

    public static void showSnackbar(String message, View root) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

}
