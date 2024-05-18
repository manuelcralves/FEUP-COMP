package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    int limit_stack = 0;
    int current_stack = 0;
    int limit_locals = 0;
    int conditionalAux = 0;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOpInstruction) {
        StringBuilder code = new StringBuilder();
        Element operand = unaryOpInstruction.getOperand();
        code.append(generators.apply(operand));
        updateStackLimits(1); // Push the operand onto the stack

        Type type = operand.getType();
        switch (type.toString()) {
            case "INT32":
                code.append("ineg\n");
                break;
            case "FLOAT32":
                code.append("fneg\n");
                break;
            case "BOOLEAN":
                String labelTrue = "LabelTrue_" + conditionalAux;
                String labelEnd = "LabelEnd_" + conditionalAux;
                code.append("ifne ").append(labelTrue).append("\n")
                        .append("iconst_1\n").append("goto ").append(labelEnd).append("\n").append(labelTrue).append(":\n")
                        .append("iconst_0\n").append(labelEnd).append(":\n");
                conditionalAux++;
                break;
            default:
                throw new NotImplementedException("Unary operation not implemented for type: " + type);
        }
        updateStackLimits(-1); // Adjust for the operation result

        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInstruction) {
        StringBuilder code = new StringBuilder();
        code.append("ifeq LABEL_TRUE\n");
        updateStackLimits(-1); // Adjust stack for conditional operation
        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInstruction) {
        return "goto " + gotoInstruction.getLabel() + NL;
    }

    private String generateOpCond(OpCondInstruction opCondInstruction) {
        StringBuilder code = new StringBuilder();
        Operation operation = opCondInstruction.getCondition().getOperation();
        String jmpLabel = "LABEL_TRUE";

        switch (operation.getOpType()) {
            case LTH:
                code.append("if_icmplt ").append(jmpLabel).append("\n");
                break;
            case GTH:
                code.append("if_icmpgt ").append(jmpLabel).append("\n");
                break;
            case LTE:
                code.append("if_icmple ").append(jmpLabel).append("\n");
                break;
            case GTE:
                code.append("if_icmpge ").append(jmpLabel).append("\n");
                break;
            default:
                throw new NotImplementedException("Operation not implemented: " + operation.getOpType());
        }
        updateStackLimits(-2); // Adjust stack for conditional operation

        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL).append(NL);

        var superName = ollirResult.getOllirClass().getSuperClass() != null ?
                ollirResult.getOllirClass().getSuperClass() :
                "java/lang/Object";
        code.append(".super ").append(superName).append(NL).append(NL);

        // generate a single constructor method
        String defaultConstructor = String.format("""
        ; default constructor
        .method public <init>()V
           aload_0
           invokespecial %s/<init>()V
           return
        .end method
        """, superName);
        code.append(defaultConstructor).append(NL);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        if(methodName.equals("main")){
            modifier += "static ";
        }

        String params = method.getParams().stream()
                .map(param -> paramTypeToSignature(param.getType()))
                .collect(Collectors.joining());

        String returnTypes = returnTypeToSignature(method.getReturnType());

        code.append("\n.method ").append(modifier).append(method.getMethodName())
                .append("(").append(params).append(")").append(returnTypes).append(NL);

        // Add limits
        limit_stack = 0;
        current_stack = 0;
        limit_locals = calculateLocalsLimit(method);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            code.append(instCode);
        }

        code.insert(code.indexOf(NL) + 1, TAB + ".limit stack " + limit_stack + NL);
        code.insert(code.indexOf(NL) + 1, TAB + ".limit locals " + limit_locals + NL);

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private int calculateLocalsLimit(Method method) {
        Set<Integer> virtualRegisters = new TreeSet<>();
        virtualRegisters.add(0);

        for(Descriptor descriptor : method.getVarTable().values()) {
            virtualRegisters.add(descriptor.getVirtualReg());
        }

        return virtualRegisters.size();
    }

    private String paramTypeToSignature(Type type) {
        return switch (type.toString()) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "FLOAT32" -> "F";
            case "DOUBLE64" -> "D";
            case "LONG64" -> "J";
            case "REFERENCE" -> "L" + type.toString().replace('.', '/') + ";";
            case "STRING[]" -> "[Ljava/lang/String;";
            case "INT32[]" -> "[I";
            default -> throw new NotImplementedException("Unsupported parameter type: " + type);
        };
    }

    private String returnTypeToSignature(Type type) {
        return switch (type.toString()) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "FLOAT32" -> "F";
            case "DOUBLE64" -> "D";
            case "LONG64" -> "J";
            case "REFERENCE" -> "L" + type.toString().replace('.', '/') + ";";
            case "STRING[]" -> "[Ljava/lang/String;";
            case "VOID" -> "V";
            case "INT32[]" -> "[I";
            default -> throw new NotImplementedException("Unsupported return type: " + type);
        };
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // get register
        var reg = getVirtualReg(operand);
        if (reg == -1) {
            throw new NotImplementedException("Variable not found in variable table: " + operand.getName());
        }

        if(operand instanceof ArrayOperand) {
            return code.append("iastore").append(NL).toString();
        }

        ElementType elemType = operand.getType().getTypeOfElement();

        String operation = STORE_OPERATIONS.get(elemType);
        if (operation == null) {
            return "Error Storing!";
        }

        updateStackLimits(-1); // Store operation pops value from stack

        return code.append(operation).append(" ").append(reg).append(NL).toString();
    }

    private static final Map<ElementType, String> STORE_OPERATIONS = Map.of(
            ElementType.INT32, "istore",
            ElementType.BOOLEAN, "istore",
            ElementType.OBJECTREF, "astore",
            ElementType.ARRAYREF, "astore"
    );

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        updateStackLimits(1); // ldc pushes value onto the stack
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = getVirtualReg(operand);
        if (reg == -1) {
            throw new NotImplementedException("Variable not found in variable table: " + operand.getName());
        }
        updateStackLimits(1); // iload pushes value onto the stack
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // Load values for the left and right operands onto the stack
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));
        updateStackLimits(2); // Push two operands onto the stack

        // Get the operation code from the operation type
        String op = getOperation(binaryOp.getOperation());

        // Append the operation to the code
        code.append(op).append(NL);
        updateStackLimits(-1); // Binary operation pops two operands and pushes one result

        // Handle boolean operations with specific control flow adjustments
        if (isBooleanOperation(binaryOp.getOperation())) {
            String labelTrue = "TRUE" + conditionalAux;
            String labelNext = "NEXT" + conditionalAux;

            code.append(" iconst_0\n")
                    .append("\tgoto ").append(labelNext).append("\n")
                    .append(labelTrue).append(":\n")
                    .append("\ticonst_1\n")
                    .append(labelNext).append(":\n");

            conditionalAux++;  // Ensure the label numbers are incremented to maintain uniqueness
            updateStackLimits(1); // Adjust for the boolean operation result
        }

        return code.toString();
    }

    private boolean isBooleanOperation(Operation operation) {
        return switch (operation.getOpType()) {
            case EQ, GTH, GTE, LTH, LTE, NEQ -> true;
            default -> false;
        };
    }

    private String getOperation(Operation operation) {
        return switch (operation.getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            case ANDB -> "iand";
            case NOTB -> "ifeq";
            default -> throw new NotImplementedException(operation.getOpType());
        };
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            Element operand = returnInst.getOperand();
            code.append(generators.apply(operand));
            updateStackLimits(1); // Push return value onto the stack

            var returnType = operand.getType().toString();

            switch (returnType) {
                case "INT32", "BOOLEAN" -> code.append("ireturn").append(NL);
                case "FLOAT32" -> code.append("freturn").append(NL);
                case "LONG64" -> code.append("lreturn").append(NL);
                case "DOUBLE64" -> code.append("dreturn").append(NL);
                case "REFERENCE" -> code.append("areturn").append(NL);
                case "INT32[]" -> code.append("ireturn").append(NL);
                default -> throw new NotImplementedException("Unsupported return type: " + returnType);
            }
        } else {
            code.append("return").append(NL);
        }

        updateStackLimits(-1); // Return operation pops the value from the stack

        return code.toString();
    }

    private static final Map<String, String> TYPE_DESCRIPTOR = Map.of(
            "INT32", "I",
            "BOOLEAN", "Z",
            "FLOAT32", "F",
            "DOUBLE64", "D",
            "LONG64", "J",
            "REFERENCE", "L",
            "STRING[]", "[Ljava/lang/String;"
    );

    private static final Map<String, String> TYPE_LOAD_INST = Map.of(
            "INT32", "iload",
            "BOOLEAN", "iload",
            "FLOAT32", "fload",
            "REFERENCE", "aload"
    );

    private String getTypeDescriptor(Type type) {
        String descriptor = TYPE_DESCRIPTOR.get(type.toString());
        if (descriptor == null) {
            throw new NotImplementedException("Unsupported type: " + type);
        }
        return descriptor;
    }

    private String getLoadInstruction(Type type) {
        String instruction = TYPE_LOAD_INST.get(type.toString());
        if (instruction == null) {
            throw new NotImplementedException("Load instruction missing for type: " + type);
        }
        return instruction;
    }

    private String generateGetField(GetFieldInstruction getField) {
        Operand object = getField.getObject();
        Operand field = getField.getField();

        updateStackLimits(1); // aload pushes the object reference onto the stack
        updateStackLimits(1); // getfield pushes the field value onto the stack

        return String.format("\taload %d\n\tgetfield %s/%s %s\n",
                getVirtualReg(object),
                currentMethod.getOllirClass().getClassName().replace('.', '/'),
                field.getName(),
                getTypeDescriptor(field.getType())
        );
    }

    private String generatePutField(PutFieldInstruction putField) {
        Operand object = putField.getObject();
        Operand field = putField.getField();
        Element value = putField.getValue();

        String valueCode;
        if (value instanceof LiteralElement) {
            valueCode = "\tldc " + ((LiteralElement) value).getLiteral() + "\n";
            updateStackLimits(1); // ldc pushes the value onto the stack
        } else if (value instanceof Operand) {
            valueCode = String.format("\t%s %d\n", getLoadInstruction(value.getType()), getVirtualReg((Operand) value));
            updateStackLimits(1); // Load instruction pushes the value onto the stack
        } else {
            throw new NotImplementedException("Unsupported value type: " + value);
        }

        updateStackLimits(2); // aload pushes the object reference and putfield pops both and pushes nothing

        return String.format("\taload %d\n%s\tputfield %s/%s %s\n",
                getVirtualReg(object),
                valueCode,
                currentMethod.getOllirClass().getClassName().replace('.', '/'),
                field.getName(),
                getTypeDescriptor(field.getType())
        );
    }

    private String generateCall(CallInstruction call) {
        StringBuilder code = new StringBuilder();

        // Load the arguments onto the stack
        for (Element operand : call.getOperands()) {
            code.append(generators.apply(operand));
            updateStackLimits(1); // Each argument pushes a value onto the stack
        }

        String methodName = call.getMethodName().toString();
        String className = call.getClass().getName().replace(".", "/");
        String arguments = call.getOperands().stream()
                .map(op -> paramTypeToSignature(op.getType()))
                .collect(Collectors.joining());

        switch (call.getInvocationType()) {
            case invokestatic:
                code.append("invokestatic ").append(className).append("/")
                        .append(methodName).append("(").append(arguments).append(")")
                        .append(returnTypeToSignature(call.getReturnType())).append(NL);
                updateStackLimits(-call.getOperands().size()); // Pop all arguments
                if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    updateStackLimits(1); // Push the return value
                }
                break;
            case invokevirtual:
                code.append("invokevirtual ").append(className).append("/")
                        .append(methodName).append("(").append(arguments).append(")")
                        .append(returnTypeToSignature(call.getReturnType())).append(NL);
                updateStackLimits(-call.getOperands().size() - 1); // Pop all arguments and object reference
                if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    updateStackLimits(1); // Push the return value
                }
                break;
            case invokespecial:
                code.append("invokespecial ").append(className).append("/")
                        .append(methodName).append("(").append(arguments).append(")")
                        .append(returnTypeToSignature(call.getReturnType())).append(NL);
                updateStackLimits(-call.getOperands().size() - 1); // Pop all arguments and object reference
                if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    updateStackLimits(1); // Push the return value
                }
                break;
            case NEW:
                code.append("new ").append(className).append(NL)
                        .append("dup").append(NL)
                        .append("invokespecial ").append(className).append("/<init>()V").append(NL);
                updateStackLimits(2); // new and dup push the object reference twice
                break;
            default:
                throw new NotImplementedException("Unsupported invocation type: " + call.getInvocationType());
        }

        return code.toString();
    }

    private int getVirtualReg(Operand operand) {
        Descriptor descriptor = currentMethod.getVarTable().get(operand.getName());
        return descriptor != null ? descriptor.getVirtualReg() : -1;
    }

    private void updateStackLimits(int update) {
        this.current_stack += update;
        if(this.current_stack > this.limit_stack) {
            this.limit_stack = this.current_stack;
        }
    }

    public static int getLocalLimits(Method method) {
        Set<Integer> virtualRegisters = new TreeSet<>();
        virtualRegisters.add(0);

        for(Descriptor descriptor : method.getVarTable().values()) {
            virtualRegisters.add(descriptor.getVirtualReg());
        }

        return virtualRegisters.size();
    }
}
