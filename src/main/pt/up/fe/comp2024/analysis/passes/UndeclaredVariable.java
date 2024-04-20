package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

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

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        String varRefName = varRefExpr.get("name");
        if (scopeStack.stream().noneMatch(scope -> scope.contains(varRefName))) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varRefExpr),
                    NodeUtils.getColumn(varRefExpr),
                    String.format("Variable '%s' is not declared in the current scope.", varRefName),
                    null
            ));
        }
        return null;
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        scopeStack.clear();
        return super.analyze(root, table);
    }
}
