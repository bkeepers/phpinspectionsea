package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalTransformations;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class DefaultValueInElseBranchInspector extends BasePhpInspection {
    private static final String message = "Assignment in this branch shall be moved before if";

    @NotNull
    public String getShortName() {
        return "DefaultValueInElseBranchInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpIf(If ifStatement) {
                /* skip ifs without else */
                final Else elseStatement = ifStatement.getElseBranch();
                if (null == elseStatement) {
                    return;
                }


                /* collect all group statement for further analysis */
                final List<GroupStatement> groupStatementsList = new LinkedList<>();
                groupStatementsList.add(ExpressionSemanticUtil.getGroupStatement(ifStatement));
                for (ControlStatement elseIf : ifStatement.getElseIfBranches()) {
                    groupStatementsList.add(ExpressionSemanticUtil.getGroupStatement(elseIf));
                }
                groupStatementsList.add(ExpressionSemanticUtil.getGroupStatement(elseStatement));


                /* collect assignments or stop inspecting when structure expectations are not met */
                final LinkedList<AssignmentExpression> assignmentsList = new LinkedList<>();
                for (GroupStatement group : groupStatementsList) {
                    /* only one expression in group statement */
                    if (null == group || 1 != ExpressionSemanticUtil.countExpressionsInGroup(group)) {
                        groupStatementsList.clear();
                        return;
                    }

                    /* find assignments, take comments in account */
                    AssignmentExpression assignmentExpression = null;
                    for (PsiElement ifChild : group.getChildren()) {
                        /* assignment expression check */
                        if (ifChild instanceof Statement && ifChild.getFirstChild() instanceof AssignmentExpression) {
                            final AssignmentExpression branchAssignment  = (AssignmentExpression) ifChild.getFirstChild();
                            final PhpPsiElement branchAssignmentVariable = branchAssignment.getVariable();
                            /* target value is variable/property */
                            if (branchAssignmentVariable instanceof Variable || branchAssignmentVariable instanceof FieldReference) {
                                assignmentExpression = branchAssignment;
                            }
                            break;
                        }
                    }
                    if (null == assignmentExpression) {
                        groupStatementsList.clear();
                        return;
                    }

                    assignmentsList.add(assignmentExpression);
                }
                groupStatementsList.clear();


                /* ensure all assignments has one subject, define the one to compare with */
                final PhpPsiElement subjectToCompareWith = assignmentsList.peekFirst().getVariable();
                if (null == subjectToCompareWith) {
                    assignmentsList.clear();
                    return;
                }
                /* now check all assignments against subject identity */
                PhpPsiElement subjectFromExpression;
                for (AssignmentExpression subjectAssignmentExpression : assignmentsList) {
                    /* ensure target variable is discoverable */
                    subjectFromExpression = subjectAssignmentExpression.getVariable();
                    if (null == subjectFromExpression) {
                        assignmentsList.clear();
                        return;
                    }

                    /* check assignment type, work with siblings due to lack of API */
                    PsiElement operation = subjectFromExpression.getNextSibling();
                    if (operation instanceof PsiWhiteSpace) {
                        operation = operation.getNextSibling();
                    }
                    /* assignment operator check */
                    if (PhpTokenTypes.opASGN != operation.getNode().getElementType()) {
                        assignmentsList.clear();
                        return;
                    }

                    /* ensure target variables matches */
                    if (!PsiEquivalenceUtil.areElementsEquivalent(subjectToCompareWith, subjectFromExpression)) {
                        assignmentsList.clear();
                        return;
                    }
                }


                /* verify candidate value: array/string/number/constant */
                final PhpPsiElement candidate = assignmentsList.getLast().getValue();
                assignmentsList.clear();
                if (this.isDefaultValueCandidateFits(candidate)) {
                    /* point the problem out */
                    holder.registerProblem(elseStatement.getFirstChild(), message, ProblemHighlightType.WEAK_WARNING);
                }
            }

            private boolean isDefaultValueCandidateFits(PhpPsiElement candidate) {
                /* quick check on expression type basis */
                if (
                    candidate instanceof StringLiteralExpression ||
                    candidate instanceof ArrayCreationExpression ||
                    candidate instanceof ConstantReference ||
                    candidate instanceof Variable
                ) {
                    return true;
                }

                /* numbers check needs additional checks */
                //noinspection RedundantIfStatement
                if (candidate instanceof PhpExpression && PhpElementTypes.NUMBER == candidate.getNode().getElementType()) {
                    return true;
                }

                return false;
            }
        };
    }
}