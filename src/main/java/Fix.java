import com.github.javaparser.ast.CompilationUnit;
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
        // SourceRoot is a tool that read and writes Java files from packages on a certain root directory.
        // In this case the root directory is found by taking the root from the current Maven module,
        // with src/main/resources appended.
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(Fix.class).resolve("src/main/resources"));
        // Our sample is in the root of this directory, so no package name.
        CompilationUnit cu = sourceRoot.parse("", "Blabla.java");

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(IfStmt n, Void arg) {
                // I figured out what to get and what to cast simply by looking at the AST in the debugger! 
                Expression condExpr = n.getCondition();
                if (condExpr instanceof BinaryExpr) {
                    BinaryExpr cond = (BinaryExpr) condExpr;
                    if (cond.getOperator() == BinaryExpr.Operator.NOT_EQUALS && n.getElseStmt().isPresent()) {
                        /* It's a good idea to clone nodes that you move around.
                            JavaParser (or you) might get confused about who their parent is!
                        */
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
                Optional<Visitable> simpleName = Optional.empty();
                final Expression scope = n.getScope();
                if (scope instanceof EnclosedExpr) {
                    simpleName = ((EnclosedExpr) scope).getInner()
                            .filter(i -> i instanceof CastExpr)
                            .map(i -> (CastExpr) i)
                            .filter(i -> i.getExpression() instanceof ThisExpr)
                            .map(t -> new NameExpr(n.getName()));
                }
                return simpleName.orElse(super.visit(n, arg));
            }
        }, null);

        // This saves back the file we read with the changes we made. Easy!
        sourceRoot.saveAll();
    }
}
