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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import net.micode.notes.R;

/**
 * PasswordDialog provides a password input dialog for locked notes.
 * When a note has a password set, this dialog is shown before allowing
 * the user to view or edit the note content.
 *
 * Usage:
 * <pre>
 * PasswordDialog dialog = new PasswordDialog(context, correctPassword, new OnPasswordVerifiedListener() {
 *     public void onVerified() {
 *         // Password is correct, open the note
 *     }
 *     public void onCancelled() {
 *         // User cancelled, go back
 *     }
 * });
 * dialog.show();
 * </pre>
 */
public class PasswordDialog {

    /**
     * Listener interface for password verification results
     */
    public interface OnPasswordVerifiedListener {
        /**
         * Called when the entered password matches the correct password
         */
        void onVerified();

        /**
         * Called when the user cancels the dialog
         */
        void onCancelled();
    }

    private Context mContext;
    private String mCorrectPassword;
    private OnPasswordVerifiedListener mListener;
    private AlertDialog mDialog;

    /**
     * Create a new PasswordDialog
     * @param context the activity context
     * @param correctPassword the correct password to verify against
     * @param listener listener for verification results
     */
    public PasswordDialog(Context context, String correctPassword,
                          OnPasswordVerifiedListener listener) {
        mContext = context;
        mCorrectPassword = correctPassword;
        mListener = listener;
    }

    /**
     * Show the password input dialog.
     * The dialog contains an EditText for password input and OK/Cancel buttons.
     * If the entered password matches, onVerified() is called.
     * If it doesn't match, a Toast is shown and the dialog stays open.
     * If cancelled, onCancelled() is called.
     */
    public void show() {
        // Create password input field
        final EditText passwordInput = new EditText(mContext);
        passwordInput.setHint(R.string.hint_password);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(50, 20, 50, 20);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.dialog_password_title);
        builder.setView(passwordInput);

        // Set up the positive (OK) button with custom click handling
        builder.setPositiveButton(android.R.string.ok, null); // Will override below

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mListener != null) {
                    mListener.onCancelled();
                }
            }
        });

        mDialog = builder.create();
        mDialog.show();

        // Override the positive button to do custom validation
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        String enteredPassword = passwordInput.getText().toString();

                        if (TextUtils.isEmpty(enteredPassword)) {
                            passwordInput.setError(mContext.getString(R.string.hint_password));
                            return;
                        }

                        if (enteredPassword.equals(mCorrectPassword)) {
                            // Password matches
                            mDialog.dismiss();
                            if (mListener != null) {
                                mListener.onVerified();
                            }
                        } else {
                            // Password doesn't match
                            passwordInput.setError(mContext.getString(R.string.password_incorrect));
                            passwordInput.selectAll();
                        }
                    }
                });

        // Request focus on the password input and show keyboard
        passwordInput.requestFocus();
    }

    /**
     * Dismiss the dialog if it's showing
     */
    public void dismiss() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }
}
