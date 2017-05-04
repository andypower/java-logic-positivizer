import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.util.Optional;

public class Fix {
    public static void main(String[] args) throws IOException {
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(Fix.class).resolve("src/main/resources"));
        CompilationUnit cu = sourceRoot.parse("", "Blabla.java");

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(IfStmt n, Void arg) {
                Expression condExpr = n.getCondition();
                if (condExpr instanceof BinaryExpr) {
                    BinaryExpr cond = (BinaryExpr) condExpr;
                    if (cond.getOperator() == BinaryExpr.Operator.NOT_EQUALS && n.getElseStmt().isPresent()) {
                        Statement thenStmt = n.getThenStmt().clone();
                        Statement elseStmt = n.getElseStmt().get().clone();
                        n.setThenStmt(elseStmt);
                        n.setElseStmt(thenStmt);
                        cond.setOperator(BinaryExpr.Operator.EQUALS);
                    }
                }
                return super.visit(n, arg);
            }

            @Override
            public Visitable visit(FieldAccessExpr n, Void arg) {
                Optional<Visitable> simpleName = n.getScope()
                        .filter(s -> s instanceof EnclosedExpr)
                        .map(s -> (EnclosedExpr) s)
                        .flatMap(s -> s.getInner()
                                .filter(i -> i instanceof CastExpr)
                                .map(i -> (CastExpr) i)
                                .filter(i -> i.getExpression() instanceof ThisExpr)
                                .map(t -> new NameExpr(n.getName()))
                        );

                return simpleName.orElse(super.visit(n, arg));
            }
        }, null);

        sourceRoot.saveAll();
    }
}
