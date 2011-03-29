package fdtmaven;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.maven.ide.eclipse.project.MavenProjectChangedEvent;
import org.maven.ide.eclipse.project.configurator.AbstractProjectConfigurator;
import org.maven.ide.eclipse.project.configurator.ProjectConfigurationRequest;

import com.powerflasher.fdt.core.outermodel.FDTModel2;
import com.powerflasher.fdt.core.outermodel.ProjectModel;

public class FDTConfigurator extends AbstractProjectConfigurator {

	public static final String FDTMAVEN_PREFIX = "FDTM_";

	@Override
	public void configure(ProjectConfigurationRequest request,
			IProgressMonitor monitor) throws CoreException {


		internalConfigure(getMavenProject(request, monitor),
				request.getProject(), monitor);

	}

	@Override
	public void mavenProjectChanged(MavenProjectChangedEvent event,
			IProgressMonitor monitor) throws CoreException {
		super.mavenProjectChanged(event, monitor);


		internalConfigure(event.getMavenProject().getMavenProject(), event
				.getMavenProject().getProject(), monitor);
	}

	private void internalConfigure(MavenProject mavenProject, IProject project,
			IProgressMonitor monitor) {

		Set<Artifact> artifacts = mavenProject.getArtifacts();


		IPathVariableManager variableManager = project.getWorkspace()
				.getPathVariableManager();

		ProjectModel projectModel = FDTModel2.getInstance().getProjectModel(
				project);

		if (projectModel != null) {

			try {

				SAXBuilder sxb = new SAXBuilder();
				Document document = sxb.build(project.getFolder(".settings")
						.getFile("com.powerflasher.fdt.classpath")
						.getLocation().toString());

				Element root = document.getRootElement();

				for (Iterator i = root.getChildren("AS3Classpath").iterator(); i
						.hasNext();) {
					Element element = (Element) i.next();
					if (element instanceof Element) {
						if (((Element) element).getText().startsWith(
								FDTMAVEN_PREFIX)) {
							i.remove();
						}
					}
				}

				for (Artifact artifact : artifacts) {
					if (isFlashDependencyArtifact(artifact)) {

						try {

							String name = getArtifactVariableName(artifact);

							variableManager.setURIValue(name, artifact
									.getFile().toURI());

							String linkedResourceName = name + ".swc";

							if (artifact.getFile().isDirectory()) {
								IFolder linkedResource = project
										.getFolder(linkedResourceName);
								if (!linkedResource.exists()) {
									linkedResource.createLink(new Path(name),
											IResource.NONE, monitor);
								}
							} else {

								IFile linkedResource = project
										.getFile(linkedResourceName);
								if (!linkedResource.exists()) {
									linkedResource.createLink(new Path(name),
											IResource.NONE, monitor);
								}
							}

							Element element = new Element("AS3Classpath");
							element.setAttribute(new Attribute(
									"generateProblems", "false"));
							element.setAttribute(new Attribute("sdkBased",
									"false"));
							element.setAttribute(new Attribute("type", "lib"));
							element.setAttribute(new Attribute(
									"useAsSharedCode", "false"));
							element.setText(linkedResourceName);

							root.addContent(element);

						} catch (CoreException e) {
							throw new RuntimeException(
									"Can't set linked resource variable", e);
						}

					}
				}

				XMLOutputter output = new XMLOutputter(Format.getPrettyFormat());
				output.output(document,
						new FileOutputStream(project.getFolder(".settings")
								.getFile("com.powerflasher.fdt.classpath")
								.getLocation().toFile()));

				project.refreshLocal(IFile.DEPTH_INFINITE, monitor);

			} catch (IOException e) {
				throw new RuntimeException("IOException", e);
			} catch (JDOMException e) {
				throw new RuntimeException("JDOMException", e);
			} catch (CoreException e) {
				throw new RuntimeException(
						"Can't set linked resource variable", e);
			}

		}

	}

	

	private boolean isFlashDependencyArtifact(Artifact artifact) {

		return "swc".equals(artifact.getType())
				&& artifact.getGroupId() != null
				&& !artifact.getGroupId().startsWith("com.adobe.flex.");

	}

	private String getArtifactVariableName(Artifact artifact) {

		String allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";

		StringBuffer buffer = new StringBuffer(FDTMAVEN_PREFIX);

		for (char c : artifact.toString().toCharArray()) {
			if (allowedChars.indexOf(c) >= 0) {
				buffer.append(c);
			} else {
				buffer.append("_");
			}
		}

		return buffer.toString();

	}

	protected MavenProject getMavenProject(ProjectConfigurationRequest request,
			IProgressMonitor monitor) throws CoreException {
		return request.getMavenProject();
	}

}
