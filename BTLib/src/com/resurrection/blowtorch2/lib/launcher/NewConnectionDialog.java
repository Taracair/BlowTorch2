package com.resurrection.blowtorch2.lib.launcher;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.validator.Validator;


public class NewConnectionDialog extends Dialog {

	ReadyListener reportto = null;

	String m_display;
	String m_host;
	int m_port;

	MudConnection m_prev;

	boolean isEditor = false;

	/** Extra account slots beyond the primary fields (index 0). */
	private final ArrayList<ServerAccount> mExtraAccounts = new ArrayList<ServerAccount>();

	public NewConnectionDialog(Context context,ReadyListener useme) {
		super(context);

		reportto = useme;
		isEditor = false;
	}

	public NewConnectionDialog(Context context,ReadyListener useme,MudConnection old) {
		super(context);

		reportto = useme;
		m_display = old.getDisplayName();
		m_host = old.getHostName();
		m_port = Integer.parseInt(old.getPortString());

		isEditor = true;
		m_prev = old;
	}

	@Override
	public void onCreate(Bundle settings) {

		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		super.onCreate(settings);

		setContentView(R.layout.newconnectiondialog);

		this.setTitle("Connection Properties:");

		Button ok = (Button)findViewById(R.id.acceptbutton);
		Button cancel = (Button)findViewById(R.id.cancelbutton);
		Button addAccount = (Button)findViewById(R.id.add_account_button);

		ok.setOnClickListener(new OKListener());
		cancel.setOnClickListener(new CANCELListener());
		if (addAccount != null) {
			addAccount.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptEditExtraAccount(-1);
				}
			});
		}

		if(isEditor) {
			EditText disp_input = (EditText)findViewById(R.id.dispinput);
			EditText host_input = (EditText)findViewById(R.id.hostinput);
			EditText port_input = (EditText)findViewById(R.id.portinput);

			disp_input.setText(m_display);
			host_input.setText(m_host);
			port_input.setText(Integer.toString(m_port));

			ArrayList<ServerAccount> existing = m_prev.getAccounts();
			if (existing != null && !existing.isEmpty()) {
				ServerAccount primary = existing.get(0);
				setPrimaryAccountFields(primary);
				for (int i = 1; i < existing.size(); i++) {
					mExtraAccounts.add(existing.get(i).copy());
				}
			}
		}
		refreshExtraAccountsList();
	}

	private void setPrimaryAccountFields(ServerAccount account) {
		EditText label = (EditText) findViewById(R.id.accountlabel_input);
		EditText login = (EditText) findViewById(R.id.logininput);
		EditText password = (EditText) findViewById(R.id.passwordinput);
		EditText mail = (EditText) findViewById(R.id.mailinput);
		if (label != null) label.setText(account.getLabel());
		if (login != null) login.setText(account.getLogin());
		if (password != null) password.setText(account.getPassword());
		if (mail != null) mail.setText(account.getMail());
	}

	private ServerAccount readPrimaryAccountFields() {
		ServerAccount account = new ServerAccount();
		EditText label = (EditText) findViewById(R.id.accountlabel_input);
		EditText login = (EditText) findViewById(R.id.logininput);
		EditText password = (EditText) findViewById(R.id.passwordinput);
		EditText mail = (EditText) findViewById(R.id.mailinput);
		if (label != null && label.getText() != null) account.setLabel(label.getText().toString().trim());
		if (login != null && login.getText() != null) account.setLogin(login.getText().toString().trim());
		if (password != null && password.getText() != null) account.setPassword(password.getText().toString());
		if (mail != null && mail.getText() != null) account.setMail(mail.getText().toString().trim());
		return account;
	}

	private void applyAccounts(MudConnection target) {
		ArrayList<ServerAccount> accounts = new ArrayList<ServerAccount>();
		ServerAccount primary = readPrimaryAccountFields();
		if (!primary.isEmpty()) {
			accounts.add(primary);
		}
		for (ServerAccount extra : mExtraAccounts) {
			if (extra != null && !extra.isEmpty()) {
				accounts.add(extra.copy());
			}
		}
		target.setAccounts(accounts);
	}

	private void refreshExtraAccountsList() {
		LinearLayout list = (LinearLayout) findViewById(R.id.extra_accounts_list);
		if (list == null) {
			return;
		}
		list.removeAllViews();
		if (mExtraAccounts.isEmpty()) {
			list.setVisibility(View.GONE);
			return;
		}
		list.setVisibility(View.VISIBLE);
		Context ctx = getContext();
		float density = ctx.getResources().getDisplayMetrics().density;
		int pad = (int) (8 * density);

		TextView hint = new TextView(ctx);
		hint.setText(R.string.launcher_extra_accounts_hint);
		hint.setTextSize(12f);
		hint.setTextColor(0xFF444444);
		hint.setPadding(0, 0, 0, pad / 2);
		list.addView(hint);

		for (int i = 0; i < mExtraAccounts.size(); i++) {
			final int index = i;
			ServerAccount account = mExtraAccounts.get(i);
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setPadding(pad, pad / 2, pad, pad / 2);
			row.setBackgroundColor(0x22FFFFFF);

			TextView preview = new TextView(ctx);
			preview.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			preview.setTextSize(13f);
			preview.setTextColor(0xFF222222);
			preview.setText(formatExtraAccountPreview(account));
			row.addView(preview);

			Button delete = new Button(ctx);
			delete.setText("Delete");
			delete.setTextSize(12f);
			delete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (index >= 0 && index < mExtraAccounts.size()) {
						mExtraAccounts.remove(index);
						refreshExtraAccountsList();
					}
				}
			});
			row.addView(delete);

			row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptEditExtraAccount(index);
				}
			});
			preview.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptEditExtraAccount(index);
				}
			});

			list.addView(row);
			View spacer = new View(ctx);
			spacer.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, (int) (4 * density)));
			list.addView(spacer);
		}
	}

	private String formatExtraAccountPreview(ServerAccount account) {
		String label = account.getLabel();
		if (label.length() == 0) {
			label = "(no label)";
		}
		String login = account.getLogin();
		if (login.length() == 0) {
			login = "—";
		}
		String mail = account.getMail();
		if (mail.length() == 0) {
			mail = "—";
		}
		String pass = account.getPassword();
		String masked = pass.length() == 0 ? "—" : maskPassword(pass);
		return getContext().getString(R.string.launcher_extra_account_row, label, login, mail, masked);
	}

	private static String maskPassword(String password) {
		int n = Math.min(password.length(), 8);
		StringBuilder sb = new StringBuilder(n);
		for (int i = 0; i < n; i++) {
			sb.append('•');
		}
		if (password.length() > 8) {
			sb.append('…');
		}
		return sb.toString();
	}

	/** @param editIndex index in {@link #mExtraAccounts}, or -1 to add a new slot */
	private void promptEditExtraAccount(final int editIndex) {
		Context ctx = getContext();
		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (12 * ctx.getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad / 2, pad, pad / 2);

		final EditText label = new EditText(ctx);
		label.setHint("Label (optional)");
		label.setSingleLine(true);
		layout.addView(label);

		final EditText login = new EditText(ctx);
		login.setHint("Login");
		login.setSingleLine(true);
		layout.addView(login);

		final EditText password = new EditText(ctx);
		password.setHint("Password");
		password.setSingleLine(true);
		password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
		layout.addView(password);

		final EditText mail = new EditText(ctx);
		mail.setHint("Mail");
		mail.setSingleLine(true);
		mail.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
		layout.addView(mail);

		final boolean editing = editIndex >= 0 && editIndex < mExtraAccounts.size();
		if (editing) {
			ServerAccount existing = mExtraAccounts.get(editIndex);
			label.setText(existing.getLabel());
			login.setText(existing.getLogin());
			password.setText(existing.getPassword());
			mail.setText(existing.getMail());
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(editing ? "Edit account slot" : "Extra account slot");
		builder.setMessage(R.string.launcher_account_plaintext_warn);
		builder.setView(layout);
		builder.setPositiveButton(editing ? "Save" : "Add", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ServerAccount account = new ServerAccount();
				account.setLabel(label.getText() != null ? label.getText().toString().trim() : "");
				account.setLogin(login.getText() != null ? login.getText().toString().trim() : "");
				account.setPassword(password.getText() != null ? password.getText().toString() : "");
				account.setMail(mail.getText() != null ? mail.getText().toString().trim() : "");
				if (editing) {
					if (account.isEmpty()) {
						mExtraAccounts.remove(editIndex);
					} else {
						mExtraAccounts.set(editIndex, account);
					}
					refreshExtraAccountsList();
				} else if (!account.isEmpty()) {
					mExtraAccounts.add(account);
					refreshExtraAccountsList();
				}
			}
		});
		builder.setNegativeButton("Cancel", null);
		builder.show();
	}

	private class OKListener implements View.OnClickListener {
		public void onClick(View v) {

			EditText disp = (EditText)findViewById(R.id.dispinput);
			EditText host = (EditText)findViewById(R.id.hostinput);
			EditText port = (EditText)findViewById(R.id.portinput);


			Validator checker = new Validator();
			checker.add(disp, Validator.VALIDATE_NOT_BLANK, "Display Name");
			checker.add(host, Validator.VALIDATE_NOT_BLANK, "Host name");
			checker.add(host, Validator.VALIDATE_HOSTNAME, "Host name");
			checker.add(port, Validator.VALIDATE_NOT_BLANK, "Port number");
			checker.add(port, Validator.VALIDATE_NUMBER, "Port number");
			checker.add(port, Validator.VALIDATE_PORT_NUMBER, "Port number");

			String result = checker.validate();
			if(result != null) {
				checker.showMessage(NewConnectionDialog.this.getContext(), result);

				return;
			}

			if(isEditor) {
				MudConnection m = m_prev.copy();
				m.setDisplayName(disp.getText().toString());
				m.setHostName(host.getText().toString());
				m.setPortString(port.getText().toString());
				applyAccounts(m);

				reportto.modify(m_prev,m);
			} else {
				MudConnection m = new MudConnection();
				m.setDisplayName(disp.getText().toString());
				m.setHostName(host.getText().toString());
				m.setPortString(port.getText().toString());
				applyAccounts(m);
				reportto.ready(m);
			}

			NewConnectionDialog.this.dismiss();
		}
	}

	private class CANCELListener implements View.OnClickListener {
		public void onClick(View v) {
			NewConnectionDialog.this.dismiss();
		}
	}

}
