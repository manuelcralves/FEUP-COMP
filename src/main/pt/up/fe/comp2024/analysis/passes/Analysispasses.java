package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Analysispasses extends AnalysisVisitor {

    private List<String> importedClasses;

    @Override
    public void buildVisitor() {
        importedClasses = new ArrayList<>();
        addVisit(Kind.IMPORT_DECL, this::visitImportDecl);
        addVisit(Kind.TYPE, this::visitType);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCall);
    }

    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        if (importedClasses == null) {
            importedClasses = new ArrayList<>();
        }
        importedClasses.add(importDecl.get("names"));
        return null;
    }

    private Void visitType(JmmNode type, SymbolTable table) {
        String typeName = type.get("name");
        if (!isBuiltInType(typeName) && (table.getClass().equals(typeName))) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(type),
                    NodeUtils.getColumn(type),
                    String.format("Class '%s' is used without being imported or defined.", typeName),
                    null
            ));
        }
        return null;
    }

    private boolean isBuiltInType(String typeName) {
        return Arrays.asList("int", "boolean", "String", "void").contains(typeName);
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {
        String methodName = methodCall.get("methodName");
        String className = methodCall.get("className");
        if (!isMethodInheritedOrImported(methodName, className, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall),
                    NodeUtils.getColumn(methodCall),
                    String.format("Method '%s' is not available in the context of class '%s'.", methodName, className),
                    null
            ));
        }
        return null;
    }

    private boolean isMethodInheritedOrImported(String methodName, String targetClassName, SymbolTable table) {
        if (targetClassName == null || targetClassName.equals(table.getClassName())) {
            return table.getMethods().contains(methodName);
        } else if (targetClassName.equals(table.getSuper())) {
            return true;
        } else {
            return table.getImports().contains(targetClassName);
        }
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        importedClasses = new ArrayList<>();
        super.analyze(root, table);
        return getReports();
    }
}
