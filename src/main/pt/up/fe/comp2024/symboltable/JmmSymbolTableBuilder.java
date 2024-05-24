package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {

    public static JmmSymbolTable build(JmmNode root) {
        var classDecl = root.getChildren(CLASS_DECL).get(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        String superClass = classDecl.getOptional("extendName").orElse(null);

        return new JmmSymbolTable(className, buildMethods(classDecl), buildReturnTypes(classDecl), buildParams(classDecl), buildLocals(classDecl), buildImports(root), superClass);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();
        classDecl.getChildren(METHOD_DECL).forEach(method -> {
            if (method.get("name").equals("main")) {
                map.put("main", new Type("void", false));
            } else {
                map.put(method.get("name"), new Type(method.getChildren(TYPE).get(0).get("name"), Boolean.parseBoolean(method.getChildren(TYPE).get(0).getOptional("isArray").orElse("false"))));
            }
        });
        return map;
    }


    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        classDecl.getChildren(METHOD_DECL).forEach(method -> {
            List<Symbol> paramsList = new ArrayList<>();
            if (method.get("name").equals("main")) {
                String type = method.getChild(0).get("name");
                boolean isArray = true; // Since main method parameter is String[]
                String parameter = method.get("args");
                paramsList.add(new Symbol(new Type(type, isArray), parameter));
            } else {
                for (JmmNode paramNode : method.getChildren("Parameters")) {
                    String type = paramNode.getChild(0).get("name");
                    boolean isArray = NodeUtils.getBooleanAttribute(paramNode, "isArray", "true");
                    String parameter = paramNode.get("name");
                    paramsList.add(new Symbol(new Type(type, isArray), parameter));
                }
            }
            map.put(method.get("name"), paramsList);
        });
        return map;
    }


    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        classDecl.getChildren(METHOD_DECL).forEach(methodNode -> {
            List<Symbol> localsList = new ArrayList<>();
            methodNode.getChildren(VAR_DECL).forEach(varNode -> {
                String varName = varNode.get("name");
                JmmNode typeNode = varNode.getChildren("Type").get(0);
                localsList.add(new Symbol(new Type(
                        typeNode.get("name"),
                        Boolean.parseBoolean(typeNode.get("isArray"))), varName
                ));
            });
            map.put(methodNode.get("name"), localsList);
        });
        map.put(classDecl.get("name"), getLocalsList(classDecl));
        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        return classDecl.getChildren(METHOD_DECL).stream().map(method -> method.get("name")).toList();
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
        root.getChildren(IMPORT_DECL).forEach(importDecl -> imports.add(importDecl.get("names").replaceAll("\\[|]|\\s", "").replace(",", ".")));
        return imports;
    }
}