package com.resurrection.blowtorch2.lib.alias;

import java.util.List;

import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;

public interface AliasEditorDialogDoneListener {
	public void newAliasDialogDone(String pre, String post, boolean enabled,
			AliasLocalEcho localEcho, List<SetVariableResponder> setVariables);
	public void editAliasDialogDone(String pre, String post, boolean enabled,
			int pos, AliasData orig, AliasLocalEcho localEcho,
			List<SetVariableResponder> setVariables);
}
