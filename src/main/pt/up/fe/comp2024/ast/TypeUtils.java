package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

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
            case METHOD_CALL_EXPR -> getMethodCallType(expr, table);

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
            case "*":
                return new Type(INT_TYPE_NAME, false);
            case "=":
                // Array initialization expression
                return new Type("int[]", true);
            default:
                throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        }
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // Placeholder implementation, expand as needed
        return new Type(INT_TYPE_NAME, false);
    }

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

    /*private static Type getMethodCallType(JmmNode methodCallType, SymbolTable table) {
        return table.getReturnType(methodCallType.get("methodName"));
    }*/

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
