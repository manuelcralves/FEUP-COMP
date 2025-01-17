package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import static pt.up.fe.comp2024.ast.Kind.*;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case BOOLEAN -> new Type("boolean", false);
            case METHOD_CALL_EXPR -> getMethodCallType(expr, table);
            case NOT -> getVarExprType(expr, table);
            case ARRAY -> getVarExprType(expr.getChild(0), table);
            //case NEW_OBJECT -> getNewObjectType(expr, table);
            case NEW_OBJECT -> new Type(table.getClassName(), false);
            case NEW_ARRAY_INT -> new Type("int", true);
            case ARRAY_INIT -> new Type("int", true);
            case LENGTH -> new Type("int", false);

            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        if (type == null) {
            System.err.println("Error: Type is null for expression " + expr);
        }

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        switch (operator) {
            case "+":
            case "-":
            case "/":
            case "*":
                return new Type(INT_TYPE_NAME, false);
            case "=":
                return new Type("int[]", true);
            case ">", "<", "&&", "||":
                return new Type("boolean", false);
            default:
                throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        }
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // Placeholder implementation, expand as needed
        String methodName = varRefExpr.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        if (varRefExpr.getKind().equals("Not")) {
            return new Type("boolean", false);
        }

        for (var locals : table.getLocalVariables(methodName)) {
            if (locals.getName().equals(varRefExpr.get("name"))) {
                retType = locals.getType();
            }
        }

        for (var params : table.getParameters(methodName)) {
            if (params.getName().equals(varRefExpr.get("name"))) {
                retType = params.getType();
            }
        }

        for (var fields : table.getFields()) {
            if (fields.getName().equals(varRefExpr.get("name"))) {
                retType = fields.getType();
            }
        }

        if (retType.isArray()) {
            return new Type("int", true);
        }

        if (retType.getName().equals("boolean")) {
            return new Type("boolean", false);
        }

        return new Type(INT_TYPE_NAME, false);
    }
    /*private static Type getNewObjectType(JmmNode newObjectExpr, SymbolTable table) {
        if (newObjectExpr.hasAttribute("className")) {
            return new Type(table.getClassName(), false);
        } else {
            System.err.println("NewObject node does not have a 'className' attribute.");
            return new Type("unknown", false);
        }
    }*/
    /**
     * Checks if sourceType can be assigned to destinationType.
     *
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // Placeholder implementation, expand as needed
        return sourceType.getName().equals(destinationType.getName());
    }

    private static Type getMethodCallType(JmmNode methodCallExpr, SymbolTable table) {
        String methodName = methodCallExpr.get("methodName");

        if (methodName.equals("println")) {
            return new Type("void", false);
        }

        Type returnType = table.getReturnType(methodName);

        if (returnType == null) {
            System.err.println("Error: Return type is null for method " + methodName);
        }

        return returnType;
    }


}
