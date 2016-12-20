package item3;

import java.io.IOException;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class UcodeCodeGen {
	static void minic2ucode(String mcFile) {
		MiniCLexer lexer;
		try {
			lexer = new MiniCLexer( new ANTLRFileStream(mcFile));
			CommonTokenStream tokens = new CommonTokenStream( lexer );
			MiniCParser parser = new MiniCParser( tokens );
			 ParseTree tree = parser.program();
			ParseTreeWalker walker = new ParseTreeWalker();
			walker.walk(new UCodeGenListener(), tree );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		minic2ucode("Ccode.c");
	}
}
