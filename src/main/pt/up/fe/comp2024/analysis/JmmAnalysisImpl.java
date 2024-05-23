package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.passes.Analysispasses;
import pt.up.fe.comp2024.analysis.passes.UndeclaredVariable;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;

import java.util.ArrayList;
import java.util.List;

public class JmmAnalysisImpl implements JmmAnalysis {

    private final List<AnalysisPass> analysisPasses;

    public JmmAnalysisImpl() {
        this.analysisPasses = List.of(new Analysispasses(), new UndeclaredVariable());
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {
        // Build the symbol table from the root node of the parser result
        SymbolTable table = JmmSymbolTableBuilder.build(parserResult.getRootNode());
        List<Report> reports = new ArrayList<>();

        // Apply each analysis pass defined in the analysisPasses list
        for (AnalysisPass pass : analysisPasses) {
            try {
                // Analyze the AST using the current pass and collect all reports
                List<Report> passReports = pass.analyze(parserResult.getRootNode(), table);
                reports.addAll(passReports);
            } catch (Exception e) {
                // Handle any exceptions during the analysis by creating an error report
                reports.add(Report.newError(Stage.SEMANTIC, -1, -1,
                        "Problem while executing analysis pass '" + pass.getClass().getSimpleName() + "'", e));
            }
        }

        // Return the result of the semantic analysis along with the symbol table and reports
        return new JmmSemanticsResult(parserResult, table, reports);
    }
}
