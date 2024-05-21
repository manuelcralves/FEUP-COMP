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

    int limitStack = 0;
    int limitMethod = 0;
    int limitLocals = 0;
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

    /**
     * Generates code for unary operations.
     * @param unaryOpInstruction the unary operation instruction.
     * @return the generated Jasmin code.
     */
    private String generateUnaryOp(UnaryOpInstruction unaryOpInstruction) {
        StringBuilder code = new StringBuilder();
        Element operand = unaryOpInstruction.getOperand();
        code.append(generators.apply(operand));

        Type type = operand.getType();
        switch (type.toString()) {
            case "INT32":
                code.append("ineg\n");
                break;
            case "FLOAT32":
                code.append("fneg\n");
                break;
            case "BOOLEAN":
                String labelTrue = generateUniqueLabel("LabelTrue");
                String labelEnd = generateUniqueLabel("LabelEnd");
                code.append("ifne ").append(labelTrue).append(NL)
                        .append("iconst_1").append(NL).append("goto ").append(labelEnd).append(NL)
                        .append(labelTrue).append(":").append(NL).append("iconst_0").append(NL)
                        .append(labelEnd).append(":").append(NL);
                break;
            default:
                throw new NotImplementedException("Unary operation not implemented for type: " + type);
        }
        return code.toString();
    }

    /**
     * Generates code for single operand conditional instructions.
     * @param singleOpCondInstruction the single operand conditional instruction.
     * @return the generated Jasmin code.
     */
    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInstruction) {
        return "ifeq LABEL_TRUE\n";
    }

    /**
     * Generates code for goto instructions.
     * @param gotoInstruction the goto instruction.
     * @return the generated Jasmin code.
     */
    private String generateGoto(GotoInstruction gotoInstruction) {
        return "goto " + gotoInstruction.getLabel() + NL;
    }

    /**
     * Generates code for conditional operations.
     * @param opCondInstruction the conditional operation instruction.
     * @return the generated Jasmin code.
     */
    private String generateOpCond(OpCondInstruction opCondInstruction) {
        StringBuilder code = new StringBuilder();

        Operation operation = opCondInstruction.getCondition().getOperation();
        String jmpLabel = "LABEL_TRUE";

        switch (operation.getOpType()) {
            case LTH -> code.append("if_icmplt ").append(jmpLabel).append(NL);
            case GTH -> code.append("if_icmpgt ").append(jmpLabel).append(NL);
            case LTE -> code.append("if_icmple ").append(jmpLabel).append(NL);
            case GTE -> code.append("if_icmpge ").append(jmpLabel).append(NL);
            default -> throw new NotImplementedException("Operation not implemented: " + operation.getOpType());
        }
        return code.toString();
    }

    /**
     * Returns the list of reports generated during the conversion.
     * @return the list of reports.
     */
    public List<Report> getReports() {
        return reports;
    }

    /**
     * Builds the Jasmin code from the OLLIR representation.
     * @return the generated Jasmin code.
     */
    public String build() {
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }
        return code;
    }

    /**
     * Generates code for a class unit.
     * @param classUnit the class unit.
     * @return the generated Jasmin code.
     */
    private String generateClassUnit(ClassUnit classUnit) {
        StringBuilder code = new StringBuilder();

        // Generate class name
        String className = classUnit.getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        String superName = Optional.ofNullable(classUnit.getSuperClass()).orElse("java/lang/Object");
        code.append(".super ").append(superName).append(NL).append(NL);

        // Generate code for all other methods
        for (var method : classUnit.getMethods()) {
            if (method.isConstructMethod()) {
                continue;
            }
            code.append(generators.apply(method));
        }

        // Generate a single constructor method
        String defaultConstructor = generateDefaultConstructor(superName);
        code.append(NL).append(defaultConstructor).append(NL);

        return code.toString();
    }

    /**
     * Generates a default constructor for the class.
     * @param superName the superclass name.
     * @return the default constructor code.
     */
    private String generateDefaultConstructor(String superName) {
        return String.format("""
            .method public <init>()V
               aload_0
               invokespecial %s/<init>()V
               return
            .end method
            """, superName);
    }

    /**
     * Generates code for a method.
     * @param method the method.
     * @return the generated Jasmin code.
     */
    private String generateMethod(Method method) {
        currentMethod = method;
        StringBuilder code = new StringBuilder();

        String modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " : "";

        if (method.getMethodName().equals("main")) {
            modifier += "static ";
        }

        String params = method.getParams().stream()
                .map(param -> paramTypeToSignature(param.getType()))
                .collect(Collectors.joining());

        String returnTypes = returnTypeToSignature(method.getReturnType());

        code.append(NL).append(".method ").append(modifier).append(method.getMethodName())
                .append("(").append(params).append(")").append(returnTypes).append(NL);

        limitStack = calculateStackLimit(method);
        limitLocals = calculateLocalLimit(method);

        code.append(TAB).append(".limit stack ").append(limitStack).append(NL);
        code.append(TAB).append(".limit locals ").append(limitLocals).append(NL);

        for (var inst : method.getInstructions()) {
            String instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            code.append(instCode);
        }

        code.append(".end method").append(NL);
        currentMethod = null;

        return code.toString();
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

    /**
     * Generates code for assignment instructions.
     * @param assign the assignment instruction.
     * @return the generated Jasmin code.
     */
    private String generateAssign(AssignInstruction assign) {
        StringBuilder code = new StringBuilder();

        code.append(generators.apply(assign.getRhs()));

        Operand lhs = (Operand) assign.getDest();
        int reg = currentMethod.getVarTable().get(lhs.getName()).getVirtualReg();
        String storeOp = STORE_OPERATIONS.get(lhs.getType().getTypeOfElement());

        if (storeOp == null) {
            return "Error Storing!";
        }

        return code.append(storeOp).append(" ").append(reg).append(NL).toString();
    }

    private static final Map<ElementType, String> STORE_OPERATIONS = Map.of(
            ElementType.INT32, "istore",
            ElementType.BOOLEAN, "istore",
            ElementType.OBJECTREF, "astore",
            ElementType.ARRAYREF, "astore"
    );

    /**
     * Generates code for single operand instructions.
     * @param singleOp the single operand instruction.
     * @return the generated Jasmin code.
     */
    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    /**
     * Generates code for literal elements.
     * @param literal the literal element.
     * @return the generated Jasmin code.
     */
    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    /**
     * Generates code for operands.
     * @param operand the operand.
     * @return the generated Jasmin code.
     */
    private String generateOperand(Operand operand) {
        int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        return "iload " + reg + NL;
    }

    /**
     * Generates code for binary operations.
     * @param binaryOp the binary operation instruction.
     * @return the generated Jasmin code.
     */
    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        String op = getOperation(binaryOp.getOperation());
        code.append(op).append(NL);

        if (isBooleanOperation(binaryOp.getOperation())) {
            String labelTrue = generateUniqueLabel("TRUE");
            String labelNext = generateUniqueLabel("NEXT");

            code.append("iconst_0").append(NL)
                    .append("goto ").append(labelNext).append(NL)
                    .append(labelTrue).append(":").append(NL)
                    .append("iconst_1").append(NL)
                    .append(labelNext).append(":").append(NL);
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

    /**
     * Generates code for return instructions.
     * @param returnInst the return instruction.
     * @return the generated Jasmin code.
     */
    private String generateReturn(ReturnInstruction returnInst) {
        StringBuilder code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            Element operand = returnInst.getOperand();
            code.append(generators.apply(operand));
            String returnType = operand.getType().toString();

            String returnOp = switch (returnType) {
                case "INT32", "BOOLEAN" -> "ireturn";
                case "FLOAT32" -> "freturn";
                case "LONG64" -> "lreturn";
                case "DOUBLE64" -> "dreturn";
                case "REFERENCE" -> "areturn";
                case "INT32[]" -> "ireturn";
                default -> throw new NotImplementedException("Unsupported return type: " + returnType);
            };
            code.append(returnOp).append(NL);
        } else {
            code.append("return").append(NL);
        }

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        Operand object = getField.getObject();
        Operand field = getField.getField();
        int reg = currentMethod.getVarTable().get(object.getName()).getVirtualReg();
        String className = currentMethod.getOllirClass().getClassName().replace('.', '/');
        String fieldType = getTypeDescriptor(field.getType());

        return String.format("\taload %d\n\tgetfield %s/%s %s\n", reg, className, field.getName(), fieldType);
    }

    private String generatePutField(PutFieldInstruction putField) {
        Operand object = putField.getObject();
        Operand field = putField.getField();
        Element value = putField.getValue();
        String valueCode = generateValueCode(value);

        int reg = currentMethod.getVarTable().get(object.getName()).getVirtualReg();
        String className = currentMethod.getOllirClass().getClassName().replace('.', '/');
        String fieldType = getTypeDescriptor(field.getType());

        return String.format("\taload %d\n%s\tputfield %s/%s %s\n", reg, valueCode, className, field.getName(), fieldType);
    }

    private String generateCall(CallInstruction call) {
        StringBuilder code = new StringBuilder();
        String type = call.getInvocationType().toString();
        String operands = call.getOperands().toString().split(" ")[1].split("\\.")[0];
        String name = Character.toUpperCase(operands.charAt(0)) + operands.substring(1);

        if (type.equals("NEW")) {
            code.append("new ").append(ollirResult.getOllirClass().getClassName()).append(NL);
        } else {
            code.append(type).append(" ").append(ollirResult.getOllirClass().getClassName()).append("/<init>()V").append(NL);
        }

        return code.toString();
    }


    private void updateStackLimits(int update) {
        this.limitMethod += update;
        if (this.limitMethod > this.limitStack) {
            this.limitStack = this.limitMethod;
        }
    }

    private int calculateLocalLimit(Method method) {
        return method.getVarTable().values().stream()
                .mapToInt(Descriptor::getVirtualReg)
                .max().orElse(0) + 1;
    }

    private int calculateStackLimit(Method method) {
        return 3; // Placeholder
    }

    public static int getLocalLimits(Method method) {
        Set<Integer> virtualRegisters = new TreeSet<>();
        virtualRegisters.add(0);

        for (Descriptor descriptor : method.getVarTable().values()) {
            virtualRegisters.add(descriptor.getVirtualReg());
        }

        return virtualRegisters.size();
    }

    /**
     * Generates a unique label for conditional operations.
     * @param base the base name for the label.
     * @return the unique label.
     */
    private String generateUniqueLabel(String base) {
        return base + "_" + conditionalAux++;
    }

    /**
     * Generates the code for a value (literal or operand).
     * @param value the value element.
     * @return the generated code for the value.
     */
    private String generateValueCode(Element value) {
        if (value instanceof LiteralElement) {
            return "\tldc " + ((LiteralElement) value).getLiteral() + NL;
        } else if (value instanceof Operand) {
            int reg = currentMethod.getVarTable().get(((Operand) value).getName()).getVirtualReg();
            return String.format("\t%s %d\n", getLoadInstruction(value.getType()), reg);
        } else {
            throw new NotImplementedException("Unsupported value type: " + value);
        }
    }

    private String getTypeDescriptor(Type type) {
        return switch (type.toString()) {
            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "FLOAT32" -> "F";
            case "DOUBLE64" -> "D";
            case "LONG64" -> "J";
            case "REFERENCE" -> "L" + type.toString().replace('.', '/') + ";";
            case "STRING[]" -> "[Ljava/lang/String;";
            case "INT32[]" -> "[I";
            default -> throw new NotImplementedException("Unsupported type: " + type);
        };
    }

    private String getLoadInstruction(Type type) {
        return switch (type.toString()) {
            case "INT32" -> "iload";
            case "BOOLEAN" -> "iload";
            case "FLOAT32" -> "fload";
            case "REFERENCE" -> "aload";
            default -> throw new NotImplementedException("Load instruction missing for type: " + type);
        };
    }
}
