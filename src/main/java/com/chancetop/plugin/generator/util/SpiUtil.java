package com.chancetop.plugin.generator.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

/**
 * @author Neal
 */
public class SpiUtil {

    public static boolean isValidField(PsiField field) {
        return field.hasModifierProperty("public") && !field.hasModifierProperty("static");
    }

    @NotNull
    public static String calculateSplitText(Document document, int statementOffset) {
        String splitText = "";
        int cur = statementOffset;
        String text = document.getText(new TextRange(cur - 1, cur));
        while (text.equals(" ") || text.equals("\t")) {
            splitText = text + splitText;
            cur--;
            if (cur < 1) {
                break;
            }
            text = document.getText(new TextRange(cur - 1, cur));
        }
        splitText = "\n" + splitText;
        return splitText;
    }

    public static void commitAndSaveDocument(PsiDocumentManager psiDocumentManager, Document document) {
        if (document != null) {
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
            psiDocumentManager.commitDocument(document);
            FileDocumentManager.getInstance().saveDocument(document);
        }
    }
}
