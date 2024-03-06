package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;
import java.util.ArrayList;
import java.util.List;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var classDecl = root.getChildren(CLASS_DECL).get(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        String superClass = classDecl.getOptional("extendName").orElse(null);

        var imports = buildImports(root);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, superClass);
    }

private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
    Map<String, Type> map = new HashMap<>();

    classDecl.getChildren(METHOD_DECL).forEach(method -> {
        JmmNode returnType = method.getChildren(TYPE).get(0);
        Type type = new Type(returnType.get("name"), Boolean.parseBoolean(returnType.getOptional("isArray").orElse("false")));
        map.put(method.get("name"), type);
    });

    return map;
}


    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            List<Symbol> paramsList = new ArrayList<>();

            for (JmmNode paramNode : method.getChildren("Parameters")) {
                String type = paramNode.getChild(0).get("name");
                boolean isArray = paramNode.getOptional("isArray").orElse("false").equals("true");
                String parameter = paramNode.get("name");
                paramsList.add(new Symbol(new Type(type, isArray), parameter));
            }

            map.put(method.get("name"), paramsList);
        }
        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).forEach(methodNode -> {
            List<Symbol> localsList = getLocalsList(methodNode);
            map.put(methodNode.get("name"), localsList);
        });

        map.put(classDecl.get("name"), getLocalsList(classDecl));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        List<Symbol> localsList = new ArrayList<>();

        methodDecl.getChildren(VAR_DECL).forEach(varNode -> {
            String varName = varNode.get("name");
            JmmNode typeNode = varNode.getChildren("Type").get(0);
            localsList.add(new Symbol(new Type(
                    typeNode.get("name"),
                    Boolean.parseBoolean(typeNode.get("isArray"))), varName
            ));
        });

        return localsList;

    }

    private static List<String> buildImports(JmmNode root) {

        List<String> imports = new ArrayList<>();

        for (var importDecl : root.getChildren(IMPORT_DECL)) {
            String namesString = importDecl.get("names");

            namesString = namesString.replaceAll("\\[|]|\\s", "");

            String[] namesArray = namesString.split(",");

            String impString = String.join(".", namesArray);
            imports.add(impString);
        }
        return imports;
    }

}