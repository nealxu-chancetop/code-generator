package com.chancetop.plugin.generator.action;

import com.chancetop.plugin.generator.constant.CommonConstant;
import com.chancetop.plugin.generator.util.SpiUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Neal
 */
public class GenerateSetterAction extends PsiElementBaseIntentionAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
        if (localVariable == null) {
            return;
        }
        handleLocalVariable(localVariable, project, element);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class) != null;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
        return CommonConstant.GENERATE_PUBLIC_SETTER;
    }

    @NotNull
    @Override
    public String getText() {
        return CommonConstant.GENERATE_PUBLIC_SETTER;
    }

    private void handleLocalVariable(PsiLocalVariable localVariable, Project project, PsiElement element) {
        PsiElement parent = localVariable.getParent();
        if (!(parent instanceof PsiDeclarationStatement)) {
            return;
        }
        PsiClass psiClass = PsiTypesUtil.getPsiClass(localVariable.getType());
        String generateName = localVariable.getName();

        List<PsiField> fields = new ArrayList<>();
        for (PsiField field : psiClass.getAllFields()) {
            if (SpiUtil.isValidField(field))
                fields.add(field);
        }
        if (fields.size() == 0)
            return;
        String sourceName = "source_name";//Messages.showInputDialog(project, "source name", "Please Input Source Name", Messages.getInformationIcon());
//        if (sourceName == null || sourceName.trim().length() == 0) return;

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        String splitText = SpiUtil.calculateSplitText(document, parent.getTextOffset());

        String generateStr = generateStr(generateName, sourceName, splitText, fields);
        document.insertString(parent.getTextOffset() + parent.getText().length(), generateStr);

        SpiUtil.commitAndSaveDocument(psiDocumentManager, document);
    }

    private String generateStr(String generateName, String sourceName, String splitText, List<PsiField> fields) {
        StringBuilder builder = new StringBuilder();
        for (PsiField field : fields) {
            builder.append(splitText);
            PsiClass fieldClass = PsiTypesUtil.getPsiClass(field.getType());
            if (fieldClass == null) {
                buildEquals(builder, field, generateName, sourceName);
                continue;
            }
            if (fieldClass.isEnum()) {
                buildIfNull(builder, field, sourceName, splitText);
                buildEnum(builder, field, fieldClass, generateName, sourceName);
            } else if ("java.util.List".equals(fieldClass.getQualifiedName())) {
                buildIfNull(builder, field, sourceName, splitText);
                buildList(builder, field, generateName, sourceName);
            } else {
                buildEquals(builder, field, generateName, sourceName);
            }
        }
        return builder.toString();
    }

    private String getOddName(String name) {
        if (name.endsWith("ies")) {
            return name.substring(0, name.length() - 1) + "y";
        } else if (name.endsWith("s")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    private void buildIfNull(StringBuilder builder, PsiField field, String sourceName, String splitText) {
        if (!field.hasAnnotation("core.framework.api.validate.NotNull")) {
            builder.append("if (").append(sourceName).append('.').append(field.getName()).append(" != null)").append(splitText).append("    ");
        }
    }

    private void buildList(StringBuilder builder, PsiField field, String generateName, String sourceName) {
        builder.append(generateName).append('.').append(field.getName()).append(" = ")
            .append(sourceName).append('.').append(field.getName()).append(".stream().map(").append(getOddName(field.getName())).append(" -> { }).collect(Collectors.toList());");
    }

    private void buildEnum(StringBuilder builder, PsiField field, PsiClass fieldClass, String generateName, String sourceName) {
        builder.append(generateName).append('.').append(field.getName()).append(" = ")
            .append(fieldClass.getName()).append(".valueOf(").append(sourceName).append('.').append(field.getName()).append(".name());");
    }

    private void buildEquals(StringBuilder builder, PsiField field, String generateName, String sourceName) {
        builder.append(generateName).append('.').append(field.getName()).append(" = ")
            .append(sourceName).append('.').append(field.getName()).append(';');
    }
}
