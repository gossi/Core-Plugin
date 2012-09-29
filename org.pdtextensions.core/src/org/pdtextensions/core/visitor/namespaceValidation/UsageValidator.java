package org.pdtextensions.core.visitor.namespaceValidation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.dltk.compiler.problem.DefaultProblem;
import org.eclipse.dltk.compiler.problem.IProblem;
import org.eclipse.dltk.compiler.problem.ProblemSeverity;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.builder.IBuildContext;
import org.eclipse.dltk.core.index2.search.ISearchEngine.MatchRule;
import org.eclipse.dltk.core.search.IDLTKSearchScope;
import org.eclipse.dltk.core.search.SearchEngine;
import org.eclipse.php.internal.core.compiler.ast.nodes.ClassInstanceCreation;
import org.eclipse.php.internal.core.compiler.ast.nodes.FormalParameter;
import org.eclipse.php.internal.core.compiler.ast.nodes.FullyQualifiedReference;
import org.eclipse.php.internal.core.compiler.ast.nodes.StaticConstantAccess;
import org.eclipse.php.internal.core.compiler.ast.nodes.StaticFieldAccess;
import org.eclipse.php.internal.core.compiler.ast.nodes.StaticMethodInvocation;
import org.eclipse.php.internal.core.compiler.ast.nodes.UsePart;
import org.eclipse.php.internal.core.compiler.ast.nodes.UseStatement;
import org.eclipse.php.internal.core.compiler.ast.visitor.PHPASTVisitor;
import org.eclipse.php.internal.core.model.PhpModelAccess;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;
import org.pdtextensions.core.compiler.IPDTProblem;

/**
 * Checks a PHP sourcemodule for unresolved type references.
 * 
 * @author Robert Gruendler <r.gruendler@gmail.com>
 *
 */
@SuppressWarnings("restriction")
public class UsageValidator extends PHPASTVisitor {

	protected List<UseStatement> statements;
	private IBuildContext buildContext;

	public UsageValidator() {

		statements = new ArrayList<UseStatement>();
	}

	public UsageValidator(IBuildContext buildContext) {
		this.buildContext = buildContext;
		statements = new ArrayList<UseStatement>();

	}

	/**
	 * Adds the usestatement to the internal list of statements
	 * and checks if it can be resolved in the project.
	 * @param s
	 */
	public boolean visit(UseStatement s) {
		
		statements.add(s);

		for (UsePart part : s.getParts()) {
			// report unresolved use statements
			if (part.getAlias() == null && !isResolved(part.getNamespace())) {
				String message = "Unable to resolve type " + part.getNamespace().getFullyQualifiedName();
				reportProblem(message, IPDTProblem.UsageRelated, part.getNamespace().sourceStart(), part.getNamespace().sourceEnd());
				
			}
		}
		
		return false;
	}

	/**
	 * Checks if a FormalParameter can be resolved.
	 * @param s
	 */
	public boolean visit(FormalParameter s) {

		if (s.getParameterType() == null) {
			return false;
		}
		
		if (s.getParameterType() instanceof FullyQualifiedReference) {
			
			if (isReferenced((FullyQualifiedReference)s.getParameterType())) {
				return false;
			}
			
			IType namespace = PHPModelUtils.getCurrentNamespace(buildContext.getSourceModule(), s.sourceStart());
			
			if (namespace != null) {
				String name = namespace.getFullyQualifiedName() + "\\" + s.getParameterType().getName();
				if (isResolved(name)) {
					return false;
				}
			}
		}
		
		for (UseStatement statement : statements) {
			if (isReferenced(s, statement)) {
				return false;
			}
		}

		String message = "The type " + s.getParameterType().getName()
				+ " cannot be resolved.";

		reportProblem(message, IPDTProblem.UsageRelated, s.getParameterType().sourceStart(), s.getParameterType().sourceEnd());
		
		return false;

	}
	
	
	/**
	 * Check if ClassInstanceCreations can be resolved.
	 * @param s
	 */
	public boolean visit(ClassInstanceCreation s) {
		
		if (s.getClassName() instanceof FullyQualifiedReference) {
			
			FullyQualifiedReference fqr = (FullyQualifiedReference) s.getClassName();
			
			if (isReferenced(fqr)) {
				return false;
			}
			
			IType namespace = PHPModelUtils.getCurrentNamespace(buildContext.getSourceModule(), s.sourceStart());
			
			if (namespace != null) {
				String name = namespace.getFullyQualifiedName() + "\\" + fqr.getName();
				if (isResolved(name)) {
					return false;
				}
			}
			
			String message = "The type " + fqr.getFullyQualifiedName()
					+ " cannot be resolved.";

			reportProblem(message, IPDTProblem.Unresolvable, fqr.sourceStart(), fqr.sourceEnd());
		}
		
		return false;
	}
	
	
	@Override
	public boolean visit(StaticConstantAccess s) throws Exception {
		
		if (s.getDispatcher() instanceof FullyQualifiedReference) {
			
			FullyQualifiedReference fqr = (FullyQualifiedReference) s.getDispatcher();
			if (!isReferenced(fqr) && !isResolved(fqr)) {
				reportProblem("Unable to resolve " + fqr.getFullyQualifiedName(), IPDTProblem.UsageRelated, fqr.sourceStart(), fqr.sourceEnd());
			}
		}
		
		return false;
	}
	
	
	@Override
	public boolean visit(StaticFieldAccess s) throws Exception {
		
		if (s.getDispatcher() instanceof FullyQualifiedReference) {
			
			FullyQualifiedReference fqr = (FullyQualifiedReference) s.getDispatcher();
			if (!isReferenced(fqr) && !isResolved(fqr)) {
				reportProblem("Unable to resolve " + fqr.getFullyQualifiedName(), IPDTProblem.UsageRelated, fqr.sourceStart(), fqr.sourceEnd());
			}
		}
		
		return false;
	}
	
	@Override
	public boolean visit(StaticMethodInvocation s) throws Exception {
		
		if (s.getReceiver() instanceof FullyQualifiedReference) {
			
			FullyQualifiedReference fqr = (FullyQualifiedReference) s.getReceiver();
			if (!isReferenced(fqr) && !isResolved(fqr)) {
				reportProblem("Unable to resolve " + fqr.getFullyQualifiedName(), IPDTProblem.UsageRelated, fqr.sourceStart(), fqr.sourceEnd());
			}
		}
		
		return false;
	}
	
	

	/**
	 * Check if the FormalParameter is referenced via the UseStatement
	 * 
	 * @param param
	 * @param statement
	 * @return bool
	 */
	protected boolean isReferenced(FormalParameter param, UseStatement statement) {

		// dont't validate anything other than fqrs
		if (!(param.getParameterType() instanceof FullyQualifiedReference)) {
			return true;
		}
		
		FullyQualifiedReference fqr = (FullyQualifiedReference) param.getParameterType();

		
		if (isReferenced(fqr, statement)) {
			return true;
		}
		
		for (UsePart part : statement.getParts()) {

			if (part.getAlias() != null && fqr.getFullyQualifiedName().startsWith(part.getAlias().getName())) {
				return true;
			}
			
			if (part.getNamespace() != null
					&& param.getParameterType().getName()
							.equals(part.getNamespace().getName())) {
				return true;
			}

			if (part.getAlias() != null
					&& param.getParameterType().getName()
							.equals(part.getAlias().getName())) {
				return true;
			}
		}
		
		return isResolved(fqr);

	}
	
	/**
	 * Check if the fqr is referenced via the UseStatement
	 * 
	 * @param fqr
	 * @param statement
	 * @return bool
	 */
	private boolean isReferenced(FullyQualifiedReference fqr,
			UseStatement statement) {
		
		for (UsePart part : statement.getParts()) {

			if (part.getNamespace() == null) {
				continue;
			}
			
			if (part.getAlias() != null && fqr.getFullyQualifiedName().startsWith(part.getAlias().getName())) {
				return true;
			}
			
			if (fqr.getFullyQualifiedName().equals(part.getNamespace().getFullyQualifiedName())) {
				return true;
			}
			
			if (fqr.getFullyQualifiedName().equals(part.getNamespace().getName())) {
				return true;
			}
		}

		return false;
	}
	
	private boolean isReferenced(FullyQualifiedReference fqr) {
		
		for (UseStatement statement : statements) {
			if (isReferenced(fqr, statement)) {
				return true;
			}
		}
		
		return false;
	}
	
	
	/**
	 * Check if the fqr be resolved in the project.
	 * 
	 * @param fqr
	 * @return bool
	 */
	private boolean isResolved(FullyQualifiedReference fqr) {

		if (fqr != null) {
			return isResolved(fqr.getFullyQualifiedName());
		}
		
		return true;
	}
	
	private boolean isResolved(String fullyQualifiedReference) {
		
		if (buildContext == null) {
			return true;
		}
		
		IDLTKSearchScope searchScope = SearchEngine.createSearchScope(buildContext.getSourceModule().getScriptProject());
		IType[] types = PhpModelAccess.getDefault().findTypes(fullyQualifiedReference, MatchRule.EXACT, 0, 0, searchScope, new NullProgressMonitor());

		for (IType type : types) {
			if (fullyQualifiedReference.equals(type.getFullyQualifiedName("\\"))) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Report a reference problem.
	 * 
	 * @param message
	 * @param type
	 * @param start
	 * @param end
	 */
	@SuppressWarnings("deprecation")
	protected void reportProblem(String message, int type, int start, int end) {

		ProblemSeverity severity = ProblemSeverity.WARNING;

		int lineNo = buildContext.getLineTracker()
				.getLineInformationOfOffset(start)
				.getOffset();
		
		// TODO: how can we check against ints to use the proper constructor
		// here
		// in InterfaceMethodQuickFixProcessor.hasCorrections
		// IProblem problem = new DefaultProblem(message,
		// PEXProblem.INTERFACE_IMPLEMENTATION,
		// new String[0], severity, miss.getStart(), miss.getEnd(),lineNo);

		IProblem problem = new DefaultProblem(message,
				IPDTProblem.UsageRelated, new String[0], severity, start, end, lineNo);

		buildContext.getProblemReporter().reportProblem(problem);
	}
	
}