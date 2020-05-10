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
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Neal
 */
public class GenerateSetterAction extends PsiElementBaseIntentionAction {
    private final static String SOURCE_NAME = "source_name";

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

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        String splitText = SpiUtil.calculateSplitText(document, parent.getTextOffset());

        StringBuilder generateStr = buildClass(psiClass, generateName, splitText);
        if (generateStr.length() == 0) return;
        document.insertString(parent.getTextOffset() + parent.getText().length(), generateStr);
        SpiUtil.commitAndSaveDocument(psiDocumentManager, document);
    }

    private String getOddName(String name) {
        if (name.endsWith("ies")) {
            return name.substring(0, name.length() - 1) + "y";
        } else if (name.endsWith("s")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    private boolean buildIfNull(StringBuilder builder, PsiField field, String splitText) {
        if (!field.hasAnnotation("core.framework.api.validate.NotNull")) {
            builder.append("if (").append(SOURCE_NAME).append('.').append(field.getName()).append(" != null){").append(splitText).append("    ");
            return true;
        }
        return false;
    }

    private void buildList(StringBuilder builder, PsiField field, String generateName) {
        PsiClass genericClass = PsiTypesUtil.getPsiClass(PsiUtil.extractIterableTypeParameter(field.getType(), false));
        String oddName = getOddName(field.getName());
        if (genericClass.isEnum()) {
            builder.append(generateName).append('.').append(field.getName()).append(" = ")
                .append(SOURCE_NAME).append('.').append(field.getName()).append(".stream().map(").append(oddName).append(" -> ").append(genericClass.getName()).append(".valueOf(").append(oddName).append(".name())).collect(Collectors.toList());");
        } else {
            builder.append(generateName).append('.').append(field.getName()).append(" = ")
                .append(SOURCE_NAME).append('.').append(field.getName()).append(".stream().map(").append(oddName).append(" -> { }).collect(Collectors.toList());");
        }
    }

    private void buildEnum(StringBuilder builder, PsiField field, PsiClass fieldClass, String generateName) {
        builder.append(generateName).append('.').append(field.getName()).append(" = ")
            .append(fieldClass.getName()).append(".valueOf(").append(SOURCE_NAME).append('.').append(field.getName()).append(".name());");
    }

    private void buildEquals(StringBuilder builder, PsiField field, String generateName) {
        builder.append(generateName).append('.').append(field.getName()).append(" = ")
            .append(SOURCE_NAME).append('.').append(field.getName()).append(';');
    }

    private StringBuilder buildClass(PsiClass psiClass, String generateName, String splitText) {
        StringBuilder builder = new StringBuilder();
        List<PsiField> fields = Arrays.stream(psiClass.getAllFields()).filter(SpiUtil::isValidField).collect(Collectors.toList());
        if (fields.isEmpty()) return builder;
        fields.forEach(field -> {
            builder.append(splitText);
            PsiClass fieldClass = PsiTypesUtil.getPsiClass(field.getType());
            //basic data type || lang data type
            if (fieldClass == null || fieldClass.getQualifiedName().startsWith("java.lang")) {
                buildEquals(builder, field, generateName);
                return;
            }
            boolean isList = "java.util.List".equals(fieldClass.getQualifiedName());
            if (isList && PsiUtil.extractIterableTypeParameter(field.getType(), false).getCanonicalText().startsWith("java.lang")) {
                buildEquals(builder, field, generateName);
                return;
            }

            boolean ifNull = buildIfNull(builder, field, splitText);
            if (fieldClass.isEnum()) {
                buildEnum(builder, field, fieldClass, generateName);
            } else if (isList) {
                buildList(builder, field, generateName);
            } else {
                buildEquals(builder, field, generateName); //class equals
            }
            if (ifNull)
                builder.append(splitText).append('}');
        });
        return builder;
    }
}
