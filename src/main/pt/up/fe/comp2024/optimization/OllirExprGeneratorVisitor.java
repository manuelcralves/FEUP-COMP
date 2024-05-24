package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN, this::visitBoolean);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(NEW_OBJECT, this::visitNewObject);
        addVisit(NEW_ARRAY_INT, this::visitNewArrayInt);
        addVisit(NOT, this::visitNot);
        addVisit(ARRAY, this::visitArray);
        addVisit(ARRAY_INIT, this::ArrayInit);
        addVisit(LENGTH, this::visitLenght);
        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var intType = new Type("boolean", false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        if (!(node.getParent().getKind().equals("IfElse")) && !(node.getParent().getKind().equals("While"))) {
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE);
        }

        computation.append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);

        if (!(node.getParent().getKind().equals("IfElse")) && !(node.getParent().getKind().equals("While"))) {
            computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);
        }

        else {
            computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                    .append(rhs.getCode());
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNot(JmmNode node, Void unused) {
        var intType = TypeUtils.getExprType(node, table);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = "!" + ollirIntType + SPACE + visit(node.getChild(0)).getCode();
        return new OllirExprResult(code);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {

        String computation = "";
        var classNode = node.getJmmChild(0);
        var vis = visit(node.getJmmChild(0));
        var methodName = node.get("methodName");
        Type retType = table.getReturnType(methodName);
        var varType = "";
        StringBuilder code = new StringBuilder();
        List<String> args = new ArrayList<>();
        var a = OllirExprResult.EMPTY;

        for (int i = 1; i < node.getChildren().size(); i++) {

            if (node.getChildren().get(i).getKind().equals(METHOD_CALL_EXPR.toString())) {
                a = visit(node.getChildren().get(i));
                code.append(a.getComputation());
            }

            if (node.getChildren().get(i).getKind().equals("Length")) {
                a = visit(node.getChildren().get(i));
                code.append(a.getComputation());
            }
        }


       if (retType != null) {

           varType = OptUtils.toOllirType(retType);
           computation = OptUtils.getTemp() + varType;

           code.append(computation);
           code.append(SPACE);
           code.append(ASSIGN);
           code.append(varType);
           code.append(SPACE);
       }


        for (int i = 1; i < node.getChildren().size(); i++) {

            if (node.getChildren().get(i).getKind().equals(METHOD_CALL_EXPR.toString()) || node.getChildren().get(i).getKind().equals(LENGTH.toString())) {
                args.add("," + a.getCode());
            }

            else {
                args.add("," + visit(node.getChildren().get(i)).getCode());
            }
        }

        String result = String.join("", args);
        String invokeType = !table.getMethods().contains(node.get("methodName")) ? "invokestatic" : "invokevirtual";
        code.append(invokeType);
        code.append("(");

        if (classNode.getOptional("name").isPresent()) {
            //code.append(classNode.getOptional("name").get());
            //code.append(vis.getCode());
            String s = !table.getMethods().contains(node.get("methodName")) ? (classNode.getOptional("name").get()) : (vis.getCode());
            code.append(s);
        }
        else {
            code.append("this");
            code.append(".");
            code.append(table.getClassName());
        }

        code.append(", \"");
        code.append(methodName);
        code.append("\"");
        code.append(result);
        code.append(")");

        if (retType == null) {
            code.append(".V");
        }

        else {
            varType = OptUtils.toOllirType(retType);
            code.append(varType);
        }

        code.append(END_STMT);

        return new OllirExprResult(computation, code);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {

        StringBuilder computation = new StringBuilder();
        String resOllirType = table.getClassName();
        String code = OptUtils.getTemp() + "." + resOllirType;

        computation.append(code);
        computation.append(SPACE);
        computation.append(ASSIGN);
        computation.append(".");
        computation.append(resOllirType);
        computation.append(SPACE);
        computation.append("new(");
        computation.append(table.getClassName());
        computation.append(")").append(".");
        computation.append(resOllirType);
        computation.append(END_STMT);
        computation.append("invokespecial(");
        computation.append(code);
        computation.append(", \"<init>\").V");
        computation.append(END_STMT);

        return new OllirExprResult(code, computation);
    }
    private OllirExprResult visitArray(JmmNode node, Void unused) {
        var classNode = node.getJmmChild(0);
        var intType = TypeUtils.getExprType(node, table);
        String ret = OptUtils.toOllirType(intType);
        StringBuilder code = new StringBuilder();

        code.append(node.getChild(0).get("name"));
        code.append("[");
        var pos = visit(node.getChild(1)).getCode();
        code.append(pos);
        code.append("]");
        code.append(ret);

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitNewArrayInt(JmmNode node, Void unused) {

        StringBuilder computation = new StringBuilder();
        var resOllirType = TypeUtils.getExprType(node, table);
        String type = OptUtils.toOllirType(resOllirType);
        String code = OptUtils.getTemp() + type;

        computation.append(code);
        computation.append(SPACE);
        computation.append(ASSIGN);
        computation.append(type);
        computation.append(SPACE);
        computation.append("new(array, ");
        computation.append(visit(node.getChild(0)).getCode());
        computation.append(")");
        computation.append(type);
        computation.append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult ArrayInit(JmmNode node, Void unused) {

        StringBuilder computation = new StringBuilder();
        var resOllirType = TypeUtils.getExprType(node, table);
        String type = OptUtils.toOllirType(resOllirType);
        String code = OptUtils.getTemp() + type;

        computation.append(code);
        computation.append(SPACE);
        computation.append(ASSIGN);
        computation.append(type);
        computation.append(SPACE);
        computation.append("new(array, ");
        computation.append(node.getChildren().size()).append(".i32)").append(type);
        computation.append(END_STMT);
        computation.append("__varargs_array_0").append(type).append(SPACE).append(ASSIGN);
        computation.append(type).append(SPACE).append(code);
        computation.append(END_STMT);

        for (int i = 0; i < node.getChildren().size(); i++) {
            computation.append("__varargs_array_0").append(type).append("[");
            computation.append(i + ".i32].i32 :=.i32").append(SPACE).append(visit(node.getChild(i)).getCode());
            computation.append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitLenght(JmmNode node, Void unused) {

        StringBuilder computation = new StringBuilder();
        var resOllirType = TypeUtils.getExprType(node, table);
        String type = OptUtils.toOllirType(resOllirType);
        String code = OptUtils.getTemp() + type;

        computation.append(code);
        computation.append(SPACE);
        computation.append(ASSIGN);
        computation.append(type).append(SPACE);
        computation.append("arraylength(").append(visit(node.getChild(0)).getCode());
        computation.append(")").append(type);
        computation.append(END_STMT);


        return new OllirExprResult(code, computation);
    }

        /**
         * Default visitor. Visits every child node and return an empty result.
         *
         * @param node
         * @param unused
         * @return
         */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
