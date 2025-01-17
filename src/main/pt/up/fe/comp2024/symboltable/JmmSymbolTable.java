package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;

public class JmmSymbolTable implements SymbolTable {

    private final String className;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final List<String> imports;
    private final String superClass;

    public JmmSymbolTable(String className,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals,
                          List<String> imports,
                          String superClass) {
        this.className = className;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
        this.imports = imports;
        this.superClass = superClass;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superClass;
    }

    @Override
    public List<Symbol> getFields() {
        return Collections.unmodifiableList(locals.getOrDefault(className, Collections.emptyList()));
    }

    @Override
    public List<String> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return params.get(methodSignature);
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return Collections.unmodifiableList(locals.getOrDefault(methodSignature, Collections.emptyList()));
    }


    public Type getVariableType(String variableName, String scope) {
        // First check local variables in the given scope (method)
        List<Symbol> localVariables = locals.get(scope);
        if (localVariables != null) {
            for (Symbol symbol : localVariables) {
                if (symbol.getName().equals(variableName)) {
                    return symbol.getType();
                }
            }
        }

        // Check global fields if not found in local scope
        List<Symbol> fields = getFields();
        for (Symbol field : fields) {
            if (field.getName().equals(variableName)) {
                return field.getType();
            }
        }

        return null;  // Variable not found
    }


    public boolean hasMethod(String className, String methodName) {
        if (this.className.equals(className)) {
            return methods.contains(methodName);
        }
        return false;
    }


    public String getParentClassName(String className) {
        if (this.className.equals(className)) {
            return superClass;
        }
        return null;
    }

    public void addLocalVariable(String method, String name, String type) {
        boolean isArray = type.endsWith("[]");
        Symbol newLocal = new Symbol(new Type(type.replace("[]", ""), isArray), name);
        locals.computeIfAbsent(method, k -> new ArrayList<>()).add(newLocal);
    }


}
