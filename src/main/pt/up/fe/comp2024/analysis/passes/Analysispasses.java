package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.*;

public class Analysispasses extends AnalysisVisitor {

    private List<String> importedClasses;
    private Map<String, String> variableTypes = new HashMap<>();

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
        String varName = assignNode.get("varName");
        JmmNode exprNode = assignNode.getChildren().get(0);

        String varType = findVariableType(varName, assignNode, table);
        String exprType = resolveType(exprNode, table);

        if (varType.equals("unknown") || exprType.equals("unknown")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignNode),
                    NodeUtils.getColumn(assignNode),
                    "Cannot resolve type for variable or expression: Variable " + varName + " or Expression " + exprType,
                    null
            ));
        } else if (!typesAreCompatible(varType, exprType, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignNode),
                    NodeUtils.getColumn(assignNode),
                    "Type mismatch: cannot assign type " + exprType + " to type " + varType,
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

        // Remember the variable type
        variableTypes.put(varName, varType);

        return null;
    }

    private Void visitAllNodes(JmmNode node, SymbolTable table) {
        System.out.println("Visiting node kind: " + node.getKind());
        return null;
    }
    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        // Collecting full import name correctly
        String fullImportName = String.join(".", importDecl.get("names"));
        // Adding to importedClasses if not already present
        fullImportName = fullImportName.replaceAll("^\\[|\\]$", "");
        if (!importedClasses.contains(fullImportName)) {
            importedClasses.add(fullImportName);
        }
        // Debug output to show current state of imported classes
        System.out.println("Import registered: " + fullImportName);
        System.out.println("Current imports: " + Arrays.toString(importedClasses.toArray()));
        return null;
    }



    private boolean isTypeValid(String typeName, SymbolTable table) {
        // Check if the type is one of the basic types
        boolean isBasicType = Arrays.asList("int", "boolean", "String", "void").contains(typeName);

        // Check if the type is the name of the class being compiled
        boolean isCurrentClass = typeName.equals(((JmmSymbolTable)table).getClassName());
        System.out.println(importedClasses);
        // Check if the type is in the list of imported classes
        boolean isImported = importedClasses.contains(typeName);

        return isBasicType || isCurrentClass || isImported;
    }








    private Void visitType(JmmNode type, SymbolTable table) {
        String typeName = type.get("name");
        boolean isArray = type.getOptional("isArray").map(Boolean::parseBoolean).orElse(false);
        String fullTypeName = isArray ? typeName + "[]" : typeName;

        // Utilizing isTypeValid to check if the type is recognized
        if (!isTypeValid(typeName, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(type),
                    NodeUtils.getColumn(type),
                    "Type '" + fullTypeName + "' is not defined or imported.",
                    null
            ));
        } else {
            System.out.println("Type recognized: " + fullTypeName);
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
        System.out.println("Kind: " + node.getKind());
        switch (node.getKind()) {
            case "Type":
                String typeName = node.get("name");
                boolean isArray = node.getOptional("isArray").map(Boolean::parseBoolean).orElse(false);
                typeName += isArray ? "[]" : "";
                if (isTypeValid(typeName, table)) return typeName;
                break;
            case "MethodCallExpr":
                resolvedType = "int";
                break;
            case "Boolean":
                resolvedType = "boolean";
                break;
            case "VarRefExpr":
                String varName = node.get("name");
                // Check if the variable type is already remembered
                if (variableTypes.containsKey(varName)) {
                    resolvedType = variableTypes.get(varName);
                } else {
                    resolvedType = findVariableType(varName, node, table);
                }
                break;
            case "NewObject":
                System.out.println("Attributes of NewObject node: " + node.getAttributes());
                if (node.hasAttribute("className")) {
                    resolvedType = node.get("className");
                    rememberNewObjectType(node, resolvedType);
                } else {
                    System.err.println("NewObject node does not have a 'className' attribute.");
                }
                break;
            default:
                resolvedType = "unknown";
        }
        return resolvedType;
    }



    private void rememberNewObjectType(JmmNode newNode, String typeName) {
        JmmNode parent = newNode.getParent();
        if (parent != null && parent.getKind().equals("Assign") && parent.getChildren().size() > 0) {
            JmmNode lhs = parent.getChildren().get(0);  // Left-hand side of the assignment
            if (lhs.getKind().equals("VarRefExpr")) {
                String varName = lhs.get("name");
                // Remember the type of the variable
                variableTypes.put(varName, typeName);
                System.out.println("Remembering type: " + varName + " -> " + typeName);
            }
        }
    }





















    private String findVariableType(String varName, JmmNode contextNode, SymbolTable table) {
        // First, check if the variable type is already remembered
        if (variableTypes.containsKey(varName)) {
            return variableTypes.get(varName);
        }

        String currentMethodSignature = getCurrentMethodSignature(contextNode);

        // Check if the method signature is valid and parameters are not null
        var methodParams = table.getParameters(currentMethodSignature);
        if (methodParams == null) {
            System.err.println("Method parameters not found for method: " + currentMethodSignature);
            // Handle as a class field or imported type
            return findClassFieldType(varName, table);
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

        // If variable is not found, return unknown
        System.out.println("Variable not found: " + varName);
        return "unknown";
    }


    private String findClassFieldType(String varName, SymbolTable table) {
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

        // If not found, check if it is an imported class type
        if (importedClasses.contains(varName)) {
            System.out.println("Imported class type found: " + varName);
            return varName;
        }

        return "unknown";
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
