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
        addVisit(Kind.CALL_METHOD, this::visitMethodCall); // Change this to match the node kind in the AST output
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.NEW_ARRAY_INT, this::visitNewArray);
        addVisit(Kind.RETURN, this::visitReturn);
        addVisit(Kind.ASSIGN, this::visitAssign); // Register the visitAssign method
        setDefaultVisit(this::visitAllNodes);
    }
    private Void visitAssign(JmmNode assignNode, SymbolTable table) {
        System.out.println("Visiting assignment: " + assignNode);

        String varName = assignNode.get("varName");
        JmmNode exprNode = assignNode.getChildren().get(0);

        String varType = findVariableType(varName, assignNode, table);
        String exprType = resolveType(exprNode, table);

        System.out.println("Variable type: " + varType + ", Expression type: " + exprType);

        if (varType.equals("unknown") || exprType.equals("unknown")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignNode),
                    NodeUtils.getColumn(assignNode),
                    "Cannot resolve type for variable or expression.",
                    null
            ));
        } else if (!typesAreCompatible(varType, exprType, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignNode),
                    NodeUtils.getColumn(assignNode),
                    "Type mismatch: cannot assign " + exprType + " to " + varType,
                    null
            ));
        }

        return null;
    }





    private boolean typesAreCompatible(String varType, String exprType, SymbolTable table) {
        if (varType.equals(exprType)) {
            return true;
        }

        // Check for inheritance compatibility
        return isSubtype(exprType, varType, table);
    }

    private boolean isSubtype(String subtype, String supertype, SymbolTable table) {
        // Implement logic to determine if 'subtype' is a subclass of 'supertype'
        // This would likely involve checking the class hierarchy maintained in the symbol table
        String currentSuper = subtype;
        while (currentSuper != null && !currentSuper.isEmpty()) {
            if (currentSuper.equals(supertype)) {
                return true;
            }
            currentSuper = ((JmmSymbolTable)table).getParentClassName(currentSuper);
        }
        return false;
    }

    private Void visitReturn(JmmNode returnNode, SymbolTable table) {
        System.out.println("Visiting return statement: " + returnNode);

        JmmNode methodNode = findParentMethod(returnNode);
        if (methodNode == null) {
            System.err.println("Return statement not within a method context.");
            return null;
        }

        var returnTypeNode = methodNode.getChildren().stream()
                .filter(child -> child.getKind().equals("Type"))
                .findFirst()
                .orElse(null);

        if (returnTypeNode == null) {
            System.err.println("Method return type not found.");
            return null;
        }

        String methodReturnType = resolveType(returnTypeNode, table);
        String returnType = resolveType(returnNode.getChildren().get(0), table);

        if (!methodReturnType.equals(returnType)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnNode),
                    NodeUtils.getColumn(returnNode),
                    "Incompatible return type: expected " + methodReturnType + " but found " + returnType,
                    null
            ));
        }

        return null;
    }

    private Void visitNewArray(JmmNode newArrayNode, SymbolTable table) {
        System.out.println("Visiting new array initialization: " + newArrayNode);

        // Resolve the type of the array
        String arrayType = resolveType(newArrayNode, table);
        if (arrayType.equals("unknown")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newArrayNode),
                    NodeUtils.getColumn(newArrayNode),
                    "Cannot resolve type for new array.",
                    null
            ));
            return null;
        }

        for (JmmNode element : newArrayNode.getChildren()) {
            String elementType = resolveType(element, table);
            if (!elementType.equals("int")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(newArrayNode),
                        NodeUtils.getColumn(newArrayNode),
                        "Array initialization element type mismatch: expected int but found " + elementType,
                        null
                ));
            }
        }

        return null;
    }



    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        JmmNode typeNode = varDecl.getChildren().get(0);
        String varName = varDecl.get("name");
        String varType = resolveType(typeNode, table);

        System.out.println("Variable declaration: " + varName + ", type: " + varType);

        if (table instanceof JmmSymbolTable) {
            JmmSymbolTable jmmTable = (JmmSymbolTable) table;
            jmmTable.addLocalVariable(currentScope(varDecl), varName, varType);
        }

        return null;
    }

    private Void visitAllNodes(JmmNode node, SymbolTable table) {
        System.out.println("Visiting node kind: " + node.getKind());
        return null;
    }

    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        String fullImportName = String.join(".", importDecl.get("names"));
        if (!importedClasses.contains(fullImportName)) {
            importedClasses.add(fullImportName);
        }
        System.out.println("Import registered: " + fullImportName);
        System.out.println("Current imports: " + importedClasses); // Added to print current imports after each registration
        return null;
    }





    private boolean isTypeValid(String type, SymbolTable table) {
        return isBuiltInType(type) || importedClasses.contains(type) || type.equals(table.getClassName());
    }




    private Void visitType(JmmNode type, SymbolTable table) {
        String typeName = type.get("name");
        boolean isArray = type.getOptional("isArray").map(Boolean::parseBoolean).orElse(false);
        String fullTypeName = isArray ? typeName + "[]" : typeName;

        // Debug: Print out current state of imports
        System.out.println("Current imports: " + importedClasses);

        // Check against imported classes and built-in types
        if (isBuiltInType(typeName) || importedClasses.contains(typeName) || typeName.equals(table.getClassName())) {
            System.out.println("Type recognized: " + fullTypeName);
        } else {
            System.out.println("Type unrecognized: " + fullTypeName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(type),
                    NodeUtils.getColumn(type),
                    "Type '" + fullTypeName + "' is not defined or imported.",
                    null
            ));
        }
        return null;
    }









    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {
        System.out.println("Visiting binary operation: " + binaryOp); // Debug statement
        String operator = binaryOp.get("op");
        List<JmmNode> children = binaryOp.getChildren();
        if (children.size() == 2) {
            String leftType = resolveType(children.get(0), table);
            String rightType = resolveType(children.get(1), table);
            if (operator.equals("+") && (leftType.equals("int[]") && rightType.equals("int") || leftType.equals("int") && rightType.equals("int[]"))) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        "Cannot add an array to an integer.",
                        null
                ));
            }
            // Check for invalid operations like multiplying a boolean with an integer
            if (operator.equals("*") && ("boolean".equals(leftType) && "int".equals(rightType) || "int".equals(leftType) && "boolean".equals(rightType))) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        "Cannot multiply boolean with integer.",
                        null
                ));
            }
        }
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        System.out.println("Visiting binary expression: " + binaryExpr); // Debug statement
        String operator = binaryExpr.get("op");
        List<JmmNode> children = binaryExpr.getChildren();
        if (children.size() == 2) {
            String leftType = resolveType(children.get(0), table);
            String rightType = resolveType(children.get(1), table);

            System.out.println("Operator: " + operator + ", Left type: " + leftType + ", Right type: " + rightType);

            if (operator.equals("+")) {
                if (!leftType.equals("int") || !rightType.equals("int")) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            "Cannot add " + leftType + " with " + rightType + ".",
                            null
                    ));
                }
            } else if (operator.equals("*")) {
                if ((leftType.equals("boolean") && rightType.equals("int")) || (leftType.equals("int") && rightType.equals("boolean"))) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            "Cannot multiply boolean with integer.",
                            null
                    ));
                } else if (!leftType.equals("int") || !rightType.equals("int")) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            "Cannot multiply " + leftType + " with " + rightType + ".",
                            null
                    ));
                }
            }
        }
        return null;
    }

    private String resolveType(JmmNode node, SymbolTable table) {
        String resolvedType = "unknown";  // Default case for unknown or unhandled types
        switch (node.getKind()) {
            case "Type":
                String baseType = node.get("name");
                boolean isArray = node.getOptional("isArray").map(Boolean::parseBoolean).orElse(false);
                resolvedType = isArray ? baseType + "[]" : baseType;
                break;
            case "IntegerLiteral":
                resolvedType = "int";  // Assuming literals have a 'type' attribute
                break;
            case "BooleanLiteral":
                resolvedType = "boolean";  // Assuming literals have a 'type' attribute
                break;
            case "VarRefExpr":
                String varName = node.get("name");
                resolvedType = findVariableType(varName, node, table);
                break;
            case "NewArrayInt":
                resolvedType = "int[]";  // Explicitly set the type for new integer arrays
                break;
            case "NewObject":
                if (node.hasAttribute("name")) {
                    resolvedType = node.get("name");
                } else {
                    System.err.println("NewObject node does not contain 'name' attribute.");
                    resolvedType = "unknown";
                }
                break;
            case "MethodCallExpr":
                String methodName = node.get("methodName");
                var returnType = table.getReturnType(methodName);
                resolvedType = returnType != null ? returnType.getName() : "unknown";
                break;
            default:
                resolvedType = "unknown";  // Default case for unknown or unhandled types
        }
        System.out.println("Resolving type for node: " + node.getKind() + ", resolved type: " + resolvedType);
        return resolvedType;
    }



















    private String findVariableType(String varName, JmmNode contextNode, SymbolTable table) {
        String currentMethodSignature = getCurrentMethodSignature(contextNode);

        // Check if the method signature is valid and parameters are not null
        var methodParams = table.getParameters(currentMethodSignature);
        if (methodParams == null) {
            System.err.println("Method parameters not found for method: " + currentMethodSignature);
            return "unknown";
        }

        // Check if the variable is a local variable in the current method
        var localVar = table.getLocalVariables(currentMethodSignature).stream()
                .filter(var -> var.getName().equals(varName))
                .findFirst();
        if (localVar.isPresent()) {
            String localVarType = localVar.get().getType().getName();
            boolean isArray = localVar.get().getType().isArray();
            System.out.println("Local variable found: " + varName + ", type: " + (isArray ? localVarType + "[]" : localVarType));
            return isArray ? localVarType + "[]" : localVarType;
        }

        // Check if the variable is a parameter in the current method
        var paramVar = methodParams.stream()
                .filter(param -> param.getName().equals(varName))
                .findFirst();
        if (paramVar.isPresent()) {
            String paramVarType = paramVar.get().getType().getName();
            boolean isArray = paramVar.get().getType().isArray();
            System.out.println("Parameter found: " + varName + ", type: " + (isArray ? paramVarType + "[]" : paramVarType));
            return isArray ? paramVarType + "[]" : paramVarType;
        }

        // Check if the variable is a class field
        var fieldVar = table.getFields().stream()
                .filter(field -> field.getName().equals(varName))
                .findFirst();
        if (fieldVar.isPresent()) {
            String fieldVarType = fieldVar.get().getType().getName();
            boolean isArray = fieldVar.get().getType().isArray();
            System.out.println("Field found: " + varName + ", type: " + (isArray ? fieldVarType + "[]" : fieldVarType));
            return isArray ? fieldVarType + "[]" : fieldVarType;
        }

        System.out.println("Variable not found: " + varName);
        return "unknown";  // If no type is found, return "unknown"
    }








    private String getCurrentMethodSignature(JmmNode node) {
        JmmNode methodNode = findParentMethod(node);
        if (methodNode != null) {
            System.out.println("Current method signature: " + methodNode.get("name"));
            return methodNode.get("name");
        }
        return "";
    }


    private JmmNode findParentMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            // Check if the current node kind is "MethodDecl", which represents method declaration
            if (current.getKind().equals("MethodDecl")) {
                return current;
            }
            current = current.getParent();
        }
        return null;  // Returns null if no method declaration node is found in the ancestry
    }




    private boolean isBuiltInType(String typeName) {
        return Arrays.asList("int", "boolean", "String", "void").contains(typeName);
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {
        System.out.println("Visiting method call: " + methodCall);

        String methodName = methodCall.get("methodName");
        JmmNode objectExpr = methodCall.getChildren().get(0); // Assuming the object expression is the first child
        String className = resolveClassName(objectExpr, table);

        if (className == null || className.equals("unknown")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall),
                    NodeUtils.getColumn(methodCall),
                    "Cannot resolve class name for method call.",
                    null
            ));
            return null;
        }

        System.out.println("Method name: " + methodName);
        System.out.println("Object class name: " + className);

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
        if (className == null || className.isEmpty()) {
            return false;
        }

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
        if (objectExpr.getKind().equals("VarRefExpr")) {
            String varName = objectExpr.get("name");
            System.out.println("Resolving class name for variable: " + varName);
            var variableType = ((JmmSymbolTable)table).getVariableType(varName, currentScope(objectExpr));
            if (variableType == null) {
                System.out.println("Variable type for " + varName + " is null.");
                return "unknown";
            }
            return variableType.getName();
        } else if (objectExpr.getKind().equals("NewObject")) {
            return objectExpr.get("name");
        }
        return null;
    }


    private String currentScope(JmmNode node) {
        while (node != null && !node.getKind().equals("Method") && !node.getKind().equals("Class")) {
            node = node.getParent();
        }
        return node != null ? node.get("name") : "Global";
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        importedClasses = new ArrayList<>();

        // Print the entire AST for debugging
        System.out.println("AST Root Node: " + root.toTree());

        super.analyze(root, table);
        return getReports();
    }
}
