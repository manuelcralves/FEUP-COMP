package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

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
        importedClasses.add(importDecl.get("names"));
        return null;
    }

    private Void visitType(JmmNode type, SymbolTable table) {
        String typeName = type.get("name");
        if (!isBuiltInType(typeName) && !importedClasses.contains(typeName) && !table.getClassName().equals(typeName)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(type),
                    NodeUtils.getColumn(type),
                    String.format("Type '%s' is used without being imported or defined.", typeName),
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
        JmmNode objectExpr = methodCall.getParent(); // Assuming parent is the expression before the method call.
        String className = resolveClassName(objectExpr, table);

        if (!isMethodAvailable(methodName, className, table)) {
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

    private boolean isMethodAvailable(String methodName, String className, SymbolTable table) {
        // First, check if table is an instance of JmmSymbolTable
        if (!(table instanceof JmmSymbolTable)) {
            System.err.println("Symbol table is not compatible with JmmSymbolTable specific methods.");
            return false;
        }

        JmmSymbolTable jmmTable = (JmmSymbolTable) table;

        // Check if the method exists in the class or any of its parent classes
        if (jmmTable.hasMethod(className, methodName)) {
            return true;
        }

        // Traverse parent classes
        String parentClassName = jmmTable.getParentClassName(className);
        while (parentClassName != null) {
            if (jmmTable.hasMethod(parentClassName, methodName)) {
                return true;
            }
            parentClassName = jmmTable.getParentClassName(parentClassName);
        }

        return false;
    }

    private String resolveClassName(JmmNode objectExpr, SymbolTable table) {
        if (!(table instanceof JmmSymbolTable)) {
            System.err.println("Symbol table is not compatible with JmmSymbolTable specific methods.");
            return null;
        }
        JmmSymbolTable jmmTable = (JmmSymbolTable) table;

        switch (objectExpr.getKind()) {
            case "This":
                return table.getClassName();
            case "VarRefExpr":
                String varName = objectExpr.get("name");
                return jmmTable.getVariableType(varName, currentScope(objectExpr)).getName();
            case "NewObject":
                return objectExpr.get("name");
            default:
                return null;
        }
    }

    private String currentScope(JmmNode node) {
        // Traverse up the AST to find the nearest method or class scope.
        while (node != null && !node.getKind().equals("Method") && !node.getKind().equals("Class")) {
            node = node.getParent();
        }
        return node != null ? node.get("name") : "Global";  // Assume a global scope if no method or class is found.
    }



    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        importedClasses = new ArrayList<>();
        super.analyze(root, table);
        return getReports();
    }
}
