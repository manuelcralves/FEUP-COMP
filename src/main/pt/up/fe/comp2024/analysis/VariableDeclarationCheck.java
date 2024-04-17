package pt.up.fe.comp2024.analysis;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VariableDeclarationCheck extends AnalysisVisitor {

    private List<String> importedClasses;
    private Set<String> declaredVariables;
    private String currentScope;

    @Override
    public void buildVisitor() {
        declaredVariables = new HashSet<>();
        importedClasses = new ArrayList<>();

        // This method should add visits to the nodes where declarations, references,
        // and uses of types occur.
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.IMPORT_DECL, this::visitImportDecl);
        addVisit(Kind.TYPE, this::visitType);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentScope = method.get("name");
        declaredVariables.clear(); // Clear variables when entering a new method
        // Add method parameters to the declared variables
        table.getParameters(currentScope).forEach(param -> declaredVariables.add(param.getName()));
        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        String varName = varDecl.get("name");
        declaredVariables.add(varName); // Add the variable to the set of declared variables
        return null;
    }

    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        // Check if the "name" attribute exists and is not null
        String importedClassName = importDecl.get("names");
        if (importedClassName != null) {
            // Initialize the importedClasses list if it hasn't been initialized yet
            if (importedClasses == null) {
                importedClasses = new ArrayList<>();
            }
            // Add imported class to the list
            importedClasses.add(importedClassName);
        }
        return null;
    }



    private Void visitType(JmmNode type, SymbolTable table) {
        String typeName = type.get("name");

        // Check if the type is a native type or defined in the same file (which may not require import)
        boolean isNativeType = typeName.equals("int") || typeName.equals("boolean") || typeName.equals("void");
        boolean isCurrentClassOrSuperclass = typeName.equals(table.getClassName()) || typeName.equals(table.getSuper());

        // Assume that if a type is not imported, it is either a native type or a class from the same package
        if (!isNativeType && !isCurrentClassOrSuperclass && !importedClasses.contains(typeName)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(type),
                    NodeUtils.getColumn(type),
                    "Class '" + typeName + "' is used without being imported or defined.",
                    null)
            );
        }
        return null;
    }


    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        String varRefName = varRefExpr.get("name");
        if (!declaredVariables.contains(varRefName)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varRefExpr),
                    NodeUtils.getColumn(varRefExpr),
                    "Variable '" + varRefName + "' is not declared in the current scope.",
                    null)
            );
        }
        return null;
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        // Initialize the imported classes and declared variables before analysis
        importedClasses = new ArrayList<>();
        declaredVariables = new HashSet<>();
        currentScope = null; // Reset the current scope
        super.analyze(root, table); // Perform the analysis
        return getReports();
    }
}