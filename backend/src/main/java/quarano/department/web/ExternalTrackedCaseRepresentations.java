package quarano.department.web;

import quarano.department.TrackedCase;

/**
 * @author Oliver Drotbohm
 * @author Jens Kutzsche
 */
public interface ExternalTrackedCaseRepresentations {

	TrackedCaseSummary toSummary(TrackedCase trackedCase);

	TrackedCaseSelect toSelect(TrackedCase trackedCase);
}
