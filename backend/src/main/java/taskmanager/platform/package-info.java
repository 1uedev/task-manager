/** Small technical contracts and infrastructure shared by all modules. Currently: correlation IDs, problem responses, the product error exception and the company of the installation ({@link taskmanager.platform.CompanyContext}). */
@ApplicationModule(displayName = "Plattform", allowedDependencies = {})
package taskmanager.platform;

import org.springframework.modulith.ApplicationModule;
