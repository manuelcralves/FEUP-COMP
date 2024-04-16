package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.List;

/**
 * Checks if a variable reference is declared in the current method's scope.
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if the variable reference exists in the current method's scope
        String varRefName = varRefExpr.get("name");
        boolean variableDeclared = table.getFields().stream().anyMatch(param -> param.getName().equals(varRefName))
                || table.getParameters(currentMethod).stream().anyMatch(param -> param.getName().equals(varRefName))
                || table.getLocalVariables(currentMethod).stream().anyMatch(varDecl -> varDecl.getName().equals(varRefName));

        // If the variable is not declared, create an error report
        if (!variableDeclared) {
            String message = String.format("Variable '%s' is not declared in the current scope.", varRefName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varRefExpr),
                    NodeUtils.getColumn(varRefExpr),
                    message,
                    null)
            );
        }

        return null;
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        // Call the parent method to perform the analysis
        super.analyze(root, table);
        return getReports();
    }
}
