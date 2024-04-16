package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks if a class is used without being imported.
 */
public class Analysispasses extends AnalysisVisitor {

    private List<String> importedClasses;

    @Override
    public void buildVisitor() {
        addVisit(Kind.IMPORT_DECL, this::visitImportDecl);
        addVisit(Kind.TYPE, this::visitType);
    }

    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        // Initialize the importedClasses list if it hasn't been initialized yet
        if (importedClasses == null) {
            importedClasses = new ArrayList<>();
        }
        // Add imported class to the list
        String importedClass = importDecl.get("name");
        importedClasses.add(importedClass);
        return null;
    }

    private Void visitType(JmmNode type, SymbolTable table) {
        if (importedClasses != null) {
            String typeName = type.get("name");
            if (!importedClasses.contains(typeName)) {
                String message = String.format("Class '%s' is used without being imported.", typeName);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(type),
                        NodeUtils.getColumn(type),
                        message,
                        null)
                );
            }
        }
        return null;
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        importedClasses = new ArrayList<>();
        super.analyze(root, table);
        return getReports();
    }
}
