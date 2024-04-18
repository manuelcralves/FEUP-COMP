package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String IMPORT = "import";
    private static final String EXTENDS = "extends";
    private static final String FIELD = "field";
    private static final String PUBLIC = "public";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(VAR_REF_EXPR, this::visitVarRefDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit("Assign", this::visitAssignStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {

        //var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(0));


        StringBuilder code = new StringBuilder();

        //code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        String name = node.get("varName");

        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);


        code.append(name);
        code.append(typeString);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitImportDecl(JmmNode node, Void unused) {

        var idElem = node.get("names");
        var idList = idElem.substring(1, idElem.length() - 1).split(", ");
        StringBuilder code = new StringBuilder();
        code.append(IMPORT);
        code.append(SPACE);
        Iterator<String> it = Arrays.stream(idList).iterator();
        while (it.hasNext()){
            code.append(it.next());
            if(it.hasNext())
                code.append('.');
        }
        code.append(END_STMT);

        return code.toString();
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        int paramCurr = 0;

        // param
        code.append("(");
        var itr = node.getChildren(PARAM).iterator();
        while (itr.hasNext()){
            paramCurr++;
            var child = itr.next();
            code.append(visit(child));
            if(itr.hasNext())
                code.append(", ");
        }

        code.append(")");

        // type
        var retType = OptUtils.toOllirType(table.getReturnType(name));
        paramCurr++;
        code.append(retType);
        code.append(L_BRACKET);


        for (int i = paramCurr; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        if(node.getChildren(RETURN_STMT).isEmpty()) {
            code.append("ret");
            code.append(retType);
            code.append(END_STMT);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        String superClass = table.getSuper();
        if(superClass != null && !superClass.equals("")) {
            code.append(SPACE);
            code.append(EXTENDS);
            code.append(SPACE);
            code.append(superClass);
            code.append(SPACE);
        }
        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private Type getVarType(String v, String method){
        if(method != null){
            for(var lVar : table.getLocalVariables(method))
                if(lVar.getName().equals(v))
                    return lVar.getType();
            for(var lParam : table.getParameters(method))
                if(lParam.getName().equals(v))
                    return lParam.getType();
        }
        for(var lField : table.getFields())
            if(lField.getName().equals(v))
                return lField.getType();
        return null;
    }

    private String visitVarRefDecl(JmmNode varRefExpr, Void unused) {


        var parentOpt = varRefExpr.getAncestor(METHOD_DECL);
        if(parentOpt.isEmpty())
            parentOpt = varRefExpr.getAncestor(CLASS_DECL);
        if(parentOpt.isEmpty())
            return "";

        var parent = parentOpt.get();
        StringBuilder code = new StringBuilder();

        code.append(varRefExpr.get("name"));
        if(parent.isInstance(METHOD_DECL)) {
            var type = getVarType(varRefExpr.get("name"), parent.get("name"));
            assert type != null;
            code.append(OptUtils.toOllirType(type));
        }



        return code.toString();
    }

    private boolean isImport(String type){
        for(var i : table.getImports())
            if(i.equals(type))
                return true;
        return false;
    }

    private String visitMethodCallExpr(JmmNode methodCallExpr, Void unused) {

        var classNode = methodCallExpr.getJmmChild(0);
        var methodName = methodCallExpr.get("name");
        StringBuilder code = new StringBuilder();
        List<String> args = new ArrayList<>();

        for (int i = 1; i < methodCallExpr.getChildren().size(); i++) {
            args.add("," + visit(methodCallExpr.getChildren().get(i)));
        }

        String result = String.join("",args);

        var type = TypeUtils.getExprType(methodCallExpr, table);
        var var_type = OptUtils.toOllirType(type);

        if(classNode.getOptional("name").isPresent() && isImport(classNode.getOptional("name").get())){
            var className = classNode.getOptional("name").get();
            if(methodCallExpr.getJmmParent().getKind().equals("ExprStmt")){
                code.append("invokestatic(");
                code.append(className);
                code.append(",");
                code.append("\"");
                code.append(methodName);
                code.append("\"");
                code.append(result);
                code.append(")");
                code.append(var_type);
                code.append(END_STMT);
            }
        }
        return code.toString();
    }

    private String visitVarDecl(JmmNode varDecl, Void unused) {
        if(!varDecl.getParent().isInstance(CLASS_DECL))
            return "";

        StringBuilder code = new StringBuilder();
        code.append('.');
        code.append(FIELD);
        code.append(SPACE);
        code.append(PUBLIC);
        code.append(SPACE);
        code.append(varDecl.get("name"));
        code.append(OptUtils.toOllirType(varDecl.getJmmChild(0)));
        code.append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        return visit(node.getJmmChild(0));
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}