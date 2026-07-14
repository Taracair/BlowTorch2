package com.resurrection.blowtorch2.lib.window;

import java.util.List;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.content.Context;
import android.os.RemoteException;
import android.view.View;

public class StandardSelectionDialog extends BaseSelectionDialog {
	
	protected IConnectionBinder service;
	
	
	public StandardSelectionDialog(Context context,IConnectionBinder service)  {
		super(context);
		this.service = service;
	}
	
	


}
