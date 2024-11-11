
String getNicePrefixForJobDescription() {
    String description = ""
    def jobInfo = getJobInfo()
    if (jobInfo.pr) {
        description += "[${jobInfo.pr_repo}/${jobInfo.pr} (${jobInfo.pr_job_number})]"
    } else if (jobInfo.started_by_user) {
        description += "${jobInfo.started_by_user}"
    } else {
        description += "[${jobInfo.started_by}]"
    }
    return description
}
