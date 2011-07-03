package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * For:
 * class B(A):
 *   def __init__(self):
 *       A.__init__(self)           #  inserted
 *       print "Constructor B was called"
 *
 * User: catherine
 */
public class AddCallSuperQuickFix implements LocalQuickFix {
  private final PyClass mySuper;
  private String mySuperName;

  public AddCallSuperQuickFix(PyClass superClass, String superName) {
    mySuper = superClass;
    mySuperName = superName;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.super");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);
    if (problemFunction == null) return;
    PyFunction superInit = mySuper.findMethodByName(PyNames.INIT, false);
    StringBuilder superCall = new StringBuilder();
    PyClass klass = problemFunction.getContainingClass();
    boolean addComma = true;
    if (klass != null && klass.isNewStyleClass()) {
      addComma = false;
      if (LanguageLevel.forElement(klass).isPy3K())
        superCall.append("super().__init__(");
      else
        superCall.append("super("+klass.getName()+", self).__init__(");
    }
    else {
      superCall.append(mySuperName);
      superCall.append(".__init__(self");
    }
    StringBuilder newFunction = new StringBuilder("def __init__(self");

    PyParameter[] parameters = problemFunction.getParameterList().getParameters();
    List<String> problemParams = new ArrayList<String>();
    List<String> functionParams = new ArrayList<String>();
    String starName = null;
    String doubleStarName = null;
    for (int i = 1; i != parameters.length; i++) {
      PyParameter p = parameters[i];
      functionParams.add(p.getName());
      if (p.getText().startsWith("**")) {
        doubleStarName = p.getText();
        continue;
      }
      if (p.getText().startsWith("*")) {
        starName = p.getText();
        continue;
      }
      if (p.getDefaultValue() != null) {
        problemParams.add(p.getText());
        continue;
      }
      newFunction.append(",").append(p.getText());
    }

    parameters = superInit.getParameterList().getParameters();
    boolean addDouble = false;
    boolean addStar = false;
    for (int i = 1; i != parameters.length; i++) {
      PyParameter p = parameters[i];
      if (p.getDefaultValue() != null) continue;
      String param;
      param = p.getText();
      if (param.startsWith("**")) {
        addDouble = true;
        if (doubleStarName == null)
          doubleStarName = p.getText();
        continue;
      }
      if (param.startsWith("*")) {
        addStar = true;
        if (starName == null)
          starName = p.getText();
        continue;
      }
      if (addComma)
        superCall.append(",");
      superCall.append(param);
      if (!functionParams.contains(param))
        newFunction.append(",").append(param);
      addComma = true;
    }
    for(String p : problemParams)
      newFunction.append(",").append(p);
    if (addStar) {
      newFunction.append(",").append(starName);
      if (addComma) superCall.append(",");
      superCall.append(starName);
      addComma = true;
    }
    if (addDouble) {
      if (addComma) superCall.append(",");
      superCall.append(doubleStarName);
      newFunction.append(",").append(doubleStarName);
    }

    superCall.append(")");
    newFunction.append("):\n\t").append(superCall).append("\n\t").append(problemFunction.getStatementList().getText());

    problemFunction.replace(
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(problemFunction), PyFunction.class,
                                                             newFunction.toString()));
  }
}
