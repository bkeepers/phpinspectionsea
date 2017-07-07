package com.kalessil.phpStorm.phpInspectionsEA.inspectors.codeSmell;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Catch;
import com.jetbrains.php.lang.psi.elements.GroupStatement;
import com.jetbrains.php.lang.psi.elements.Try;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import org.jetbrains.annotations.NotNull;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class BadExceptionsProcessingInspector extends BasePhpInspection {
    private static final String messagePattern          = "Consider moving non-related statements (%c% in total) outside the try-block or refactoring the try-body into a function/method.";
    private static final String messageFailSilently     = "The exception being ignored, please don't fail silently and at least log it.";
    private static final String messageChainedException = "The exception being ignored, please log it or use chained exceptions.";

    @NotNull
    public String getShortName() {
        return "BadExceptionsProcessingInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            @Override
            public void visitPhpTry(@NotNull Try tryStatement) {
                final GroupStatement body  = ExpressionSemanticUtil.getGroupStatement(tryStatement);
                final int expressionsCount = body == null ? 0 : ExpressionSemanticUtil.countExpressionsInGroup(body);
                if (expressionsCount > 3) {
                    final String message = messagePattern.replace("%c%", String.valueOf(expressionsCount));
                    holder.registerProblem(tryStatement.getFirstChild(), message, ProblemHighlightType.WEAK_WARNING);
                }
            }

            @Override
            public void visitPhpCatch(@NotNull Catch catchStatement) {
                final Variable variable   = catchStatement.getException();
                final GroupStatement body = ExpressionSemanticUtil.getGroupStatement(catchStatement);
                if (variable != null && body != null) {
                    final String variableName = variable.getName();
                    boolean isVariableUsed    = false;
                    for (final Variable usedVariable :PsiTreeUtil.findChildrenOfType(body, Variable.class)) {
                        if (usedVariable.getName().equals(variableName)) {
                            isVariableUsed = true;
                            break;
                        }
                    }
                    if (!isVariableUsed) {
                        if (ExpressionSemanticUtil.countExpressionsInGroup(body) == 0) {
                            holder.registerProblem(variable, messageFailSilently, ProblemHighlightType.WEAK_WARNING);
                        } else {
                            holder.registerProblem(variable, messageChainedException, ProblemHighlightType.WEAK_WARNING);
                        }
                    }
                }
            }
        };
    }
}
