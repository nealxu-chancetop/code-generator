package com.chancetop.plugin.generator.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GenerateBuilderAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible("JAVA".equals(event.getData(CommonDataKeys.PSI_ELEMENT).getLanguage().getID()));
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        // TODO: insert action logic here
        Navigatable nav = event.getData(CommonDataKeys.NAVIGATABLE);
        if (nav == null || !nav.canNavigate()) return;
        String builderClassName;
        String targetClassName;

        PsiElement data = event.getData(CommonDataKeys.PSI_ELEMENT);
        String fileName = event.getData(CommonDataKeys.PSI_FILE).getName();
        if (data.getParent() instanceof PsiJavaFileImpl) {
            targetClassName = fileName.substring(0, fileName.lastIndexOf('.'));
            builderClassName = targetClassName + "Builder";
        } else {
            String className = ((PsiClassImpl) data).getName();
            builderClassName = fileName.substring(0, fileName.lastIndexOf('.')) + className + "Builder";
            targetClassName = fileName.substring(0, fileName.lastIndexOf('.')) + '.' + className;

        }

        if (nav instanceof PsiClassImpl) {
            PsiClassImpl psiClass = (PsiClassImpl) nav;
            if (psiClass.getAllFields().length == 0) {
                Messages.showWarningDialog("Can't find any field, please check class again!", "Warning");
                return;
            }
            String content = buildFieldsContext(builderClassName, targetClassName, psiClass);
            PsiFileFactory instance = PsiFileFactory.getInstance(event.getProject());
            PsiFile templeFile = instance.createFileFromText(builderClassName + ".java", psiClass.getLanguage(), content);
            saveBuildFile(event, templeFile);
        }
    }

    private String getPackageName(PsiClassImpl psiClass) {
        PsiElement context = psiClass.getContext();
        if (context instanceof PsiJavaFileImpl) {
            return ((PsiJavaFileImpl) context).getPackageName();
        }
        //only two layer
        if (context.getParent() instanceof PsiJavaFileImpl) {
            return ((PsiJavaFileImpl) context.getParent()).getPackageName();
        }
        return "";
    }

    private void saveBuildFile(AnActionEvent event, PsiFile file) {
        PsiDirectory directory = event.getData(CommonDataKeys.PSI_ELEMENT).getContainingFile().getParent();
        try {
            directory.checkCreateFile(file.getName());
        } catch (IncorrectOperationException e) {
            Messages.showInfoMessage("Already exist build file, Please delete it!", "Info");
            return;
        }

        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                VfsUtil.copyFile(this, file.getVirtualFile(), directory.getVirtualFile());
                new OpenFileDescriptor(event.getProject(), directory.findFile(file.getName()).getVirtualFile()).navigate(true);
            } catch (IOException e) {
                Messages.showErrorDialog("Builder file generate failed!", "Error");
            }
        });
    }

    private String buildFieldsContext(String builderClassName, String targetClassName, PsiClassImpl psiClass) {
        StringBuilder content = new StringBuilder(512);
        content.append("package ").append(getPackageName(psiClass)).append(';').append("\n\n");

        String tabString = "    ";
        String fieldFormat = "    public %s %s(%s %s) {\n        this.builder.%s = %s;\n        return this;\n    }\n\n";

        content.append("public class ").append(builderClassName).append(' ').append("{\n");
        content.append(tabString).append("public final ").append(targetClassName).append(" builder;\n\n");

        List<PsiField> constructFields = new ArrayList<>();
        List<PsiField> fields = new ArrayList<>();

        for (PsiField field : psiClass.getAllFields()) {
            if (field.hasAnnotation("core.framework.api.validate.NotNull")) {
                constructFields.add(field);
            } else {
                fields.add(field);
            }
        }
        content.append(buildConstructMethod(builderClassName, targetClassName, constructFields));

        for (PsiField field : fields) {
            content.append(String.format(fieldFormat, builderClassName, field.getName(), field.getType().getPresentableText(), field.getName(), field.getName(), field.getName(), field.getName()));
        }

        content.deleteCharAt(content.length() - 1);
        content.append("\n}");
        return content.toString();
    }

    private StringBuilder buildConstructMethod(String builderClassName, String targetClassName, List<PsiField> constructFields) {
        StringBuilder content = new StringBuilder(128);
        content.append("    public ").append(builderClassName).append('(');
        if (constructFields.isEmpty()) {
            content.append(") {\n").append("        this.builder = new ").append(targetClassName).append("();\n    }\n\n");
            return content;
        }
        String fieldFormat = "        this.builder.%s = %s;\n";

        for (PsiField field : constructFields) {
            content.append(field.getType().getPresentableText()).append(' ').append(field.getName()).append(", ");
        }
        content.setLength(content.length() - 2);
        content.append(") {\n");
        content.append("        this.builder = new ").append(targetClassName).append("();\n");
        for (PsiField field : constructFields) {
            content.append(String.format(fieldFormat, field.getName(), field.getName()));
        }
        content.append("    }\n\n");
        return content;
    }
}
