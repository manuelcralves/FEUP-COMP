package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.*;

/**
 * Analysis pass to check for undeclared variable usage within method scopes.
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private Set<String> declaredVariables;
    private String currentMethod;
    private Stack<Set<String>> scopeStack = new Stack<>();


    public UndeclaredVariable() {
        this.declaredVariables = new HashSet<>();
        this.currentMethod = null;
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);

        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        scopeStack.push(new HashSet<>());
        currentMethod = method.get("name");
        table.getParameters(currentMethod).forEach(param -> scopeStack.peek().add(param.getName()));
        return null;
    }
/*
    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        String varRefName = varRefExpr.get("name");
        if (scopeStack.stream().noneMatch(scope -> scope.contains(varRefName))) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varRefExpr),
                    NodeUtils.getColumn(varRefExpr),
                    String.format("Variable '%s' is not declared in the current scope ahahahha.", varRefName),
                    null
            ));
        }
        return null;
    }
*/
private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
    String varRefName = varRefExpr.get("name");
    JmmNode parentMethod = findParentMethod(varRefExpr);

    // Check if the variable is in the local scope of the method or if it is a class field
    if (!isVariableDeclared(varRefName, parentMethod, table)) {
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                "Variable '" + varRefName + "' is not declared in the current scope.",
                null
        ));
    }
    return null;
}

    private boolean isVariableDeclared(String varName, JmmNode methodNode, SymbolTable table) {
        // Check local variables and parameters in the current method scope
        if (methodNode != null) {
            List<String> parametersAndLocals = new ArrayList<>();
            table.getParameters(methodNode.get("name")).forEach(param -> parametersAndLocals.add(param.getName()));
            table.getLocalVariables(methodNode.get("name")).forEach(local -> parametersAndLocals.add(local.getName()));
            System.out.println(varName);
            if (parametersAndLocals.contains(varName) ) {
                return true;
            }
        }

        // Check class fields
        return table.getFields().stream().anyMatch(field -> field.getName().equals(varName));
    }



    private JmmNode findParentMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            // Check if the current node kind is "Method", which matches the output from your debugging
            if (current.getKind().equals("Method")) {
                return current;
            }
            current = current.getParent();
        }
        return null;  // Returns null if no method declaration node is found in the ancestry
    }



    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        scopeStack.clear();
        return super.analyze(root, table);
    }
}
