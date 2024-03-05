package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;
import static pt.up.fe.comp2024.ast.Kind.VAR_DECL;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var classDecl = root.getJmmChild(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals);
    }

private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
    Map<String, Type> map = new HashMap<>();

    classDecl.getChildren(METHOD_DECL).forEach(method -> {
        var returnTypeNode = method.getJmmChild(0); // Adjust child index based on your AST structure
        String typeName = returnTypeNode.get("type");
        boolean isArray = returnTypeNode.getOptional("isArray").orElse("false").equals("true");
        map.put(method.get("name"), new Type(typeName, isArray));
    });

    return map;
}


    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).forEach(method -> {
            List<Symbol> paramsList = method.getChildren("Param").stream() // Assuming "Param" is the kind for parameter nodes
                    .map(paramNode -> {
                        String typeName = paramNode.get("type");
                        boolean isArray = paramNode.getOptional("isArray").orElse("false").equals("true");
                        String paramName = paramNode.get("name");
                        return new Symbol(new Type(typeName, isArray), paramName);
                    })
                    .collect(Collectors.toList());

            map.put(method.get("name"), paramsList);
        });

        return map;
    }


    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).forEach(method -> {
            List<Symbol> localsList = new ArrayList<>();
            var localVars = method.getDescendants(Kind.VAR_DECL);
            for (JmmNode localVar : localVars) {
                String varName = localVar.get("name");
                String typeName = localVar.get("type");
                boolean isArray = localVar.getOptional("isArray").orElse("false").equals("true");
                localsList.add(new Symbol(new Type(typeName, isArray), varName));
            }
            map.put(method.get("name"), localsList);
        });

        return map;
    }


    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {
                    String typeName = varDecl.get("type");
                    boolean isArray = varDecl.getOptional("isArray").orElse("false").equals("true");
                    Type varType = new Type(typeName, isArray);
                    String varName = varDecl.get("name");
                    return new Symbol(varType, varName);
                })
                .collect(Collectors.toList());
    }


}
