package item3;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

public class UCodeGenListener extends MiniCBaseListener{
	ParseTreeProperty<String> newTexts = new ParseTreeProperty<String>();
	int base = 2;
	int jmp_label = 0;
	int blockNo = 1;	//전역은 1, 함수들은 2
	int whileCount = 0;
	int ifCount = 0;
	int globalVar_offset = 0;
	int localVar_offset = 0;
	List<Variable> symbol_table = new ArrayList<>();
	
	//보조 메소드
	public static boolean isNumber(char firstLetter) {
        if(firstLetter == ' ')	//비어있을 때 
            return false;
     
        if(firstLetter<'0' || firstLetter>'9')
                return false;
        
        return true;
    }
	
	private String printTab()
	{
		// 왼쪽에 11칸은 빈칸 (space 11개) - 12칸부터 코드 시작
		String blank = "";
		for(int i=0; i<11; i++)
			blank += " ";
		return blank;
	}
	
	private String printTab(String label)
	{	
		//레이블과 하나이상 공백띄우고 12칸부터 코드 시작
		String blank = label;
		for(int i=0; i<11-label.length(); i++)
			blank += " ";
		return blank;
	}
	
	private Variable lookupTable(String varName, int blockNo) {	//LITERAL을 위해!
		for(int i = symbol_table.size()-1; i>=0; i--) {	//다른 함수에 이름이 같은 변수가 있을 수 있으므로
			String cmp_varName = symbol_table.get(i).name;
			int cmp_blockNo = symbol_table.get(i).base;
			if(cmp_varName.equals(varName) && cmp_blockNo == blockNo) {
				return symbol_table.get(i);
			}
		}
		return null;
	}
	
	private void insertTable(String varName, int base, int offset, int size, int param) {
		if(blockNo == 1) {								//위치가 전역인 경우
			if(lookupTable(varName, blockNo) == null) {	//테이블에 똑같은 변수가 없으면 insert
				Variable newVar = new Variable(varName, base, offset, size, param);
				symbol_table.add(newVar);
			}
			else {
				System.out.println("전역 변수 " + varName + " 이미 있어요~");
			}
		}
		else {
			if(lookupTable(varName,blockNo) == null) {
				Variable newVar = new Variable(varName, base, offset, size, param);
				symbol_table.add(newVar);
			}
			else {
				if(lookupTable(varName, blockNo).param == 1) {	//위치가 로컬인 경우 테이블에 파라미터로 같은 변수가 있는지 확인해야 함
					System.out.println("변수 " + varName + "가 parameter로 이미 있어요~");
				}
				else {
					Variable newVar = new Variable(varName, base, offset, size, param);
					symbol_table.add(newVar);
				}
			}
		}
	}
	
	
	//내부 클래스
	public class Variable {	//변수 클래스
		String name;
		int base;
		int offset;
		int size;
		int param=0; //parameter면 1, 아니면 0
		
		public Variable(String name, int base, int offset, int size, int param) {
			this.name = name;
			this.base = base;
			this.offset = offset;
			this.size = size;
			this.param = param;
		}
		
	}
		
	
	// program	: decl+
	@Override
	public void exitProgram(MiniCParser.ProgramContext ctx) {
		super.exitProgram(ctx);
		String decl = "";
		if(ctx.getChildCount() > 0)						// decl이 하나 이상 존재할 때
			for(int i=0; i<ctx.getChildCount(); i++)
				decl += newTexts.get(ctx.decl(i));
		newTexts.put(ctx, decl);
		String finish = printTab() + "bgn " + globalVar_offset + "\n";
		finish += printTab() + "ldp\n";			//메인 호출
		finish += printTab() + "call main\n";	//.
		finish += printTab() + "end";
		System.out.println(newTexts.get(ctx) + finish);
	}
	
	// decl	: var_decl | fun_decl
	@Override
	public void exitDecl(MiniCParser.DeclContext ctx) {
		super.exitDecl(ctx);
		String decl = "";
		if(ctx.getChildCount() == 1)
		{
			if(ctx.var_decl() != null)				// decl이 var_decl인 경우
				decl += newTexts.get(ctx.var_decl());
			else									// decl이 fun_decl인 경우
				decl += newTexts.get(ctx.fun_decl());
		}
		newTexts.put(ctx, decl);
	}
	
	// var_decl	: type_spec IDENT ';' | type_spec IDENT '=' LITERAL ';'|type_spec IDENT '[' LITERAL ']' ';'
	@Override
	public void enterVar_decl(MiniCParser.Var_declContext ctx) {
		super.enterVar_decl(ctx);
		globalVar_offset++;
		String varName = ctx.getChild(1).getText();
		int base = 1;	//전역은 base 1
		int offset = globalVar_offset;
		int size;
		
		/* symbol_table에 변수 넣어주기 */
		if(ctx.getChildCount() >= 3)
		{
			if(ctx.getChildCount() == 6){	//type_spec IDENT '[' LITERAL ']' ';'인 경우
											//배열 변수이므로 size 받아와야하고 위에서 하나 증가시킨 거 뺀 size만큼 offset 증가
				size = Integer.parseInt(ctx.getChild(3).getText());
				globalVar_offset += (size - 1);
				insertTable(varName, base, offset, size, 0);
			}
			else {	//type_spec IDENT ';'인 경우와 type_spec IDENT '=' LITERAL ';'인 경우
					//변수 하나인 경우이므로 둘 다 size 1
				size = 1;
				insertTable(varName, base, offset, size, 0);
			}
		}
	}

	@Override
	public void exitVar_decl(MiniCParser.Var_declContext ctx) {
		super.exitVar_decl(ctx);
		String decl = "";
		if(ctx.getChildCount() >= 3)
		{
			String varName = ctx.getChild(1).getText();
			Variable ident = lookupTable(varName, blockNo);
			if(ident != null) {
				decl += printTab() + "sym " + ident.base + " " + ident.offset + " " + ident.size + "\n";
				if(ctx.getChildCount() == 5){
					decl += printTab() + "ldc " + ctx.getChild(3).getText() + "\n";
					decl += printTab() + "str " + ident.base + " " + ident.offset + "\n";
				}
				newTexts.put(ctx, decl);
			}
			else {
				System.out.println("[exitVar_decl] : 변수 " + varName + " 없어요~");
			}
		}
	}
	
	// type_spec	: VOID | INT
	@Override
	public void exitType_spec(MiniCParser.Type_specContext ctx) {
		super.exitType_spec(ctx);
	}
	
	// fun_decl : type_spec IDENT '(' params ')' compound_stmt
	@Override
	public void enterFun_decl(MiniCParser.Fun_declContext ctx) {
		super.enterFun_decl(ctx);
		blockNo++;
		localVar_offset = 0;
	}

	@Override
	public void exitFun_decl(MiniCParser.Fun_declContext ctx) {
		super.exitFun_decl(ctx);
		String stmt="";
		if(ctx.getChildCount() == 6)
		{
			String func_name = ctx.getChild(1).getText();	// IDENT
			stmt += printTab(func_name)	+ "proc " + localVar_offset + " 2 2\n";
			stmt += newTexts.get(ctx.params());
			stmt += newTexts.get(ctx.compound_stmt());
			if(ctx.getChild(0).getChild(0).getText().equals("void")) {
				stmt+= printTab() + "ret\n";
			}
			stmt += printTab() + "end\n";
			newTexts.put(ctx, stmt);
		}
		blockNo--;
		localVar_offset = 0;
	}
	
	// params	: param (',' param)* | VOID	|
	@Override
	public void exitParams(MiniCParser.ParamsContext ctx) {
		super.exitParams(ctx);
		String params = "";
		if(ctx.getChildCount() > 0)
		{	// param (',' param)*
			for(int i=0; i<ctx.getChildCount(); i++)
			{
				if(i % 2 == 0)
					params += newTexts.get(ctx.param(i/2));	// param
			}
		}
		newTexts.put(ctx, params);
	}
	
	// param	: type_spec IDENT | type_spec IDENT '[' ']'
	@Override 
	public void enterParam(MiniCParser.ParamContext ctx) {
		super.enterParam(ctx);
		localVar_offset++;
		String varName = ctx.getChild(1).getText();
		int base = 2;	//전역 아닌 나머지 상황에서는 base 2
		int offset = localVar_offset;
		int size;
		
		/* symbol_table에 변수 넣어주기 */
		if(ctx.getChildCount() >= 2)
		{
			size = 1;
			insertTable(varName, base, offset, size, 1);
		}
	}
	
	@Override
	public void exitParam(MiniCParser.ParamContext ctx) {
		super.exitParam(ctx);
		String param = "";
		if(ctx.getChildCount() >= 2)
		{
			String varName = ctx.getChild(1).getText();		// IDENT
			Variable ident = lookupTable(varName, blockNo);
			if(ident != null) {
				param += printTab() + "sym " + ident.base + " " + ident.offset + " " + ident.size + "\n";
				newTexts.put(ctx, param);
			}
			else {
				System.out.println("[exitParam] : 변수 " + varName + " 없어요~");
			}
		}
	}
	
	// stmt	: expr_stmt | compound_stmt | if_stmt | while_stmt | return_stmt
	@Override
	public void exitStmt(MiniCParser.StmtContext ctx) {
		super.exitStmt(ctx);
		String stmt = "";
		if(ctx.getChildCount() > 0)
		{
			if(ctx.expr_stmt() != null)				// expr_stmt일 때
				stmt += newTexts.get(ctx.expr_stmt());
			else if(ctx.compound_stmt() != null) 	// compound_stmt일 때
				stmt += newTexts.get(ctx.compound_stmt());
			else if(ctx.if_stmt() != null)			// if_stmt일 때
				stmt += newTexts.get(ctx.if_stmt());
			else if(ctx.while_stmt() != null)		// while_stmt일 때
				stmt += newTexts.get(ctx.while_stmt());
			else									// return_stmt일 때
				stmt += newTexts.get(ctx.return_stmt());
		}
		newTexts.put(ctx, stmt);
	}
	
	// expr_stmt	: expr ';'
	@Override
	public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
		super.exitExpr_stmt(ctx);
		String stmt = "";
		if(ctx.getChildCount() == 2) {
			stmt += newTexts.get(ctx.expr());	// expr
		}
		newTexts.put(ctx, stmt);
	}
	
	// while_stmt	: WHILE '(' expr ')' stmt
	@Override
	public void enterWhile_stmt(MiniCParser.While_stmtContext ctx) {
		super.enterWhile_stmt(ctx);
		whileCount++;
	}

	@Override
	public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
		super.exitWhile_stmt(ctx);
		String stmt = "";
		if(ctx.getChildCount() == 5)
		{
			stmt += printTab("$$" + jmp_label) + "nop\n";
			stmt += newTexts.get(ctx.expr());	// expr
			stmt += printTab() + "fjp $$" + (jmp_label+1) + "\n";
			stmt += newTexts.get(ctx.stmt());	// stmt
			stmt += printTab() + "ujp $$" + (jmp_label++) + "\n";
			stmt += printTab("$$" + (jmp_label++)) + "nop\n";
		}
		newTexts.put(ctx, stmt);
		whileCount--;
	}
	
	// compound_stmt	: '{' local_decl* stmt* '}'
	@Override
	public void exitCompound_stmt(MiniCParser.Compound_stmtContext ctx) {
		super.exitCompound_stmt(ctx);
		String stmt = "";
		int local_i = 0, stmt_i = 0;
		if(ctx.getChildCount() >= 2)
		{
			for(int i=1; i<ctx.getChildCount()-1; i++)
			{
				if(ctx.local_decl().contains(ctx.getChild(i)))		// local_decl인 경우
					stmt += newTexts.get(ctx.local_decl(local_i++));
				else 											// stmt인 경우
					stmt += newTexts.get(ctx.stmt(stmt_i++));
			}
		}
		newTexts.put(ctx, stmt);
	}
	
	// local_decl	: type_spec IDENT ';' | type_spec IDENT '=' LITERAL ';' | type_spec IDENT '[' LITERAL ']' ';'	;
	@Override
	public void enterLocal_decl(MiniCParser.Local_declContext ctx) {
		super.enterLocal_decl(ctx);
		localVar_offset++;
		String varName = ctx.getChild(1).getText();
		int base = 2;	//전역 아닌 나머지 상황에서는 base 2
		int offset = localVar_offset;
		int size;
		
		/* symbol_table에 변수 넣어주기 */
		if(ctx.getChildCount() >= 3)
		{
			if(ctx.getChildCount() == 6){	//type_spec  '[' LITERAL ']' ';'인 경우
											//배열 변수이므로 size 받아와야하고 위에서 하나 증가시킨 거 뺀 size만큼 offset 증가
				size = Integer.parseInt(ctx.getChild(3).getText());
				localVar_offset += (size - 1);
				insertTable(varName, base, offset, size, 0);
			}
			else {	//type_spec IDENT ';'인 경우와 type_spec IDENT '=' LITERAL ';'인 경우
					//변수 하나인 경우이므로 둘 다 size 1
				size = 1;
				insertTable(varName, base, offset, size, 0);
			}
		}
	}
	
	@Override
	public void exitLocal_decl(MiniCParser.Local_declContext ctx) {
		super.exitLocal_decl(ctx);
		String decl = "";
		if(ctx.getChildCount() >= 3)
		{
			String varName = ctx.getChild(1).getText();
			Variable ident = lookupTable(varName, blockNo);
			if(ident != null) {
				decl += printTab() + "sym " + ident.base + " " + ident.offset + " " + ident.size + "\n";
				if(ctx.getChildCount() == 5){
					decl += printTab() + "ldc " + ctx.getChild(3).getText() + "\n";
					decl += printTab() + "str " + ident.base + " " + ident.offset + "\n";
				}
				newTexts.put(ctx, decl);
			}
			else {
				System.out.println("[exitLocal_decl] : 변수 " + varName + " 없어요~");
			}
		}
	}
	
	// if_stmt	: IF '(' expr ')' stmt | IF '(' expr ')' stmt ELSE stmt;
	@Override
	public void enterIf_stmt(MiniCParser.If_stmtContext ctx) {
		super.enterIf_stmt(ctx);
		ifCount++;
	}

	@Override
	public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {
		super.exitIf_stmt(ctx);
		String stmt = "";
		if(ctx.getChildCount() >= 5)
		{
			if(ctx.getChildCount() == 5) {
				stmt += newTexts.get(ctx.expr());	// expr
				stmt += printTab() + "fjp $$" + jmp_label + "\n";
				stmt += newTexts.get(ctx.stmt(0));	// stmt
				stmt += printTab("$$" + (jmp_label++)) + "nop\n";
				newTexts.put(ctx, stmt);
			}
			else if(ctx.getChildCount() == 7)
			{
				stmt += newTexts.get(ctx.expr());	// expr
				stmt += printTab() + "fjp $$" + jmp_label + "\n";
				stmt += newTexts.get(ctx.stmt(0));	// stmt1
				stmt += printTab() + "ujp $$" + (jmp_label+1) + "\n";
				stmt += printTab("$$" + (jmp_label++)) + "nop\n";
				stmt += newTexts.get(ctx.stmt(1));	// stmt2
				stmt += printTab("$$" + ((jmp_label++))) + "nop\n";
				newTexts.put(ctx, stmt);
			}
		}
		ifCount--;
	}
	
	// return_stmt	: RETURN ';' | RETURN expr ';'
	@Override
	public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
		super.exitReturn_stmt(ctx);
		String stmt = "";
		if(ctx.getChildCount() >= 2)
		{
			if(ctx.getChildCount() == 2) {	//RETURN ';'
				stmt += printTab() + "ldc  \n";
				stmt += printTab() + "retv\n";
				newTexts.put(ctx, stmt);
			}
			else if(ctx.getChildCount() == 3) {	//RETURN expr ';'
				stmt += newTexts.get(ctx.expr());
				stmt += printTab() + "retv\n";
				newTexts.put(ctx, stmt);
			}
		}
	}
	
	// expr
	@Override
	public void exitExpr(MiniCParser.ExprContext ctx) {
		super.exitExpr(ctx);
		String s1 = null, s2 = null, s3 = null, op = null, value = null; 
		
		if(ctx.getChildCount() > 0)
		{
			// IDENT | LITERAL일 경우
			if(ctx.getChildCount() == 1) {
				s1 = ctx.getChild(0).getText();
				char s1_first = s1.charAt(0);
				if(isNumber(s1_first)){	//LITERAL - 첫 글자가 숫자
					newTexts.put(ctx, printTab() + "ldc " + s1 + "\n");
				}
				else {	//IDENT
					Variable ident = lookupTable(s1, blockNo);	//base랑 offset 가져오기
					if(ident != null) {
						newTexts.put(ctx, printTab() + "lod " + ident.base + " " + ident.offset + "\n");
					}
					else {
						if(blockNo == 2) {	//로컬에서 변수 찾고 있는 경우 해당하는 로컬 변수가 없으면 전역에서 찾기
							ident = lookupTable(s1, 1);
							if(ident != null) {
								newTexts.put(ctx, printTab() + "lod " + ident.base + " " + ident.offset + "\n");
							}
							else {
								System.out.println("[exitExpr1] : 변수 " + s1 + " 없어요~");
							}
						}
						System.out.println("[exitExpr2] : 변수 " + s1 + " 없어요~");
					}
				}
			}
			// pre-operation
			else if(ctx.getChildCount() == 2)
			{
				op = ctx.getChild(0).getText();
				s1 = newTexts.get(ctx.expr(0));
				switch(op) {
				case "-":
					op = "neg\n";
					break;
				case "+":
					if(s1.charAt(0) == '-')	//음수일 경우에 다시 양수로 만들기
						op = "neg\n";
					break;
				case "--":
					op = "decop\n";
					break;
				case "++":
					op = "incop\n";
					break;
				case "!":
					op = "notop\n";
					break;
				}
				newTexts.put(ctx, s1 + printTab() + op);
			}
			else if(ctx.getChildCount() == 3)
			{
				// '(' expr ')'
				if(ctx.getChild(0).getText().equals("("))
				{
					s1 += newTexts.get(ctx.expr(0));
					newTexts.put(ctx, s1);
				}
				// IDENT '=' expr
				else if(ctx.getChild(1).getText().equals("="))
				{
					s1 = ctx.getChild(0).getText();
					s2 = newTexts.get(ctx.expr(0));
					Variable ident = lookupTable(s1, blockNo);
					if(ident != null) {
						value = s2;
						value += printTab() + "str " + ident.base + " " + ident.offset + "\n";
						newTexts.put(ctx, value);
					}
					else {
						if(blockNo == 2) {	//로컬에서 변수 찾고 있는 경우 해당하는 로컬 변수가 없으면 전역에서 찾기
							ident = lookupTable(s1, 1);
							if(ident != null) {
								newTexts.put(ctx, printTab() + "lod " + ident.base + " " + ident.offset + "\n");
							}
							else {
								System.out.println("[exitExpr3] : 변수 " + s1 + " 없어요~");
							}
						}
						System.out.println("[exitExpr4] : 변수 " + s1 + " 없어요~");
					}
				}
				// binary operation
				else
				{
					s1 = newTexts.get(ctx.expr(0));
					s2 = newTexts.get(ctx.expr(1));
					op = ctx.getChild(1).getText();
					switch(op) {
					case "*":
						op = "mult\n";
						break;
					case "/":
						op = "div\n";
						break;
					case "%":
						op = "modop\n";
						break;
					case "+":
						op = "add\n";
						break;
					case "-":
						op = "sub\n";
						break;
					case "==":
						op = "eq\n";
						break;
					case "!=":
						op = "ne\n";
						break;
					case "<=":
						op = "le\n";
						break;
					case "<":
						op = "lt\n";
						break;
					case ">=":
						op = "ge\n";
						break;
					case ">":
						op = "gt\n";
						break;
					case "and":
						op = "andop\n";
						break;
					case "or":
						op = "orop\n";
						break;
					}
					newTexts.put(ctx, s1 + s2 + "           " + op);
				}
			}
			// IDENT '(' args ')' |  IDENT '[' expr ']'일 경우
			else if(ctx.getChildCount() == 4)
			{
				s1 = ctx.getChild(0).getText();	// IDENT
				if(ctx.args() != null)				// args
				{
					value = printTab() + "ldp\n"; 
					value += newTexts.get(ctx.args());
					value += printTab() + "call " + s1 + "\n";
					newTexts.put(ctx, value);
				}
				else								// expr
				{
					Variable ident = lookupTable(s1, blockNo);
					if(ident != null) {
						value = newTexts.get(ctx.expr(0));
						value += printTab() + "lda " + ident.base + " " + ident.offset + "\n";
						value += printTab() + "add\n";
						newTexts.put(ctx, value);
					}
					else {
						if(blockNo == 2) {	//로컬에서 변수 찾고 있는 경우 해당하는 로컬 변수가 없으면 전역에서 찾기
							ident = lookupTable(s1, 1);
							if(ident != null) {
								newTexts.put(ctx, printTab() + "lod " + ident.base + " " + ident.offset + "\n");
							}
							else {
								System.out.println("[exitExpr5] : 변수 " + s1 + " 없어요~");
							}
						}
						System.out.println("[exitExpr6] : 변수 " + s1 + " 없어요~");
					}
				}
			}
			// IDENT '[' expr ']' '=' expr
			else
			{
				s1 = ctx.getChild(0).getText();	// IDENT
				s2 = newTexts.get(ctx.expr(0));	// expr
				s3 = newTexts.get(ctx.expr(1));	// expr
				Variable ident = lookupTable(s1, blockNo);
				if(ident != null) {
					value = s2;
					value += printTab() + "lda " + ident.base + " " + ident.offset + "\n";
					value += printTab() + "add\n";
					value += s3; 
					value += printTab() + "sti\n";
					newTexts.put(ctx, value);
				}
				else {
					if(blockNo == 2) {	//로컬에서 변수 찾고 있는 경우 해당하는 로컬 변수가 없으면 전역에서 찾기
						ident = lookupTable(s1, 1);
						if(ident != null) {
							newTexts.put(ctx, printTab() + "lod " + ident.base + " " + ident.offset + "\n");
						}
						else {
							System.out.println("[exitExpr7] : 변수 " + s1 + " 없어요~");
						}
					}
					System.out.println("[exitExpr8] : 변수 " + s1 + " 없어요~");
				}
			}
		}
	}

	
	// args	: expr (',' expr)* | ;
	@Override
	public void exitArgs(MiniCParser.ArgsContext ctx) {
		super.exitArgs(ctx);
		String args = "";
		if(ctx.getChildCount() >= 0)
		{
			for(int i=0; i<ctx.getChildCount(); i++)
			{
				if(i % 2 == 0)
					args += newTexts.get(ctx.expr(i/2));	// expr
			}
		}
		newTexts.put(ctx, args);
	}
}
