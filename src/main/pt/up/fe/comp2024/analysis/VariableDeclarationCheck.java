package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;

public class VariableDeclarationCheck extends AJmmVisitor<List<Report>, Boolean> {


    private Boolean defaultVisit(JmmNode node, List<Report> reports) {
        for (var child : node.getChildren()) {
            visit(child, reports);
        }
        return true;
    }

    @Override
    protected void buildVisitor() {

    }
}
