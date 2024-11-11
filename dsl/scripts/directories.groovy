def dirs = [
    [
        id: 'private',
        display: '[Private]',
    ],
    [
        id: 'private/infra',
        display: '[Infra automations]',
    ],
    [
        id: 'private/snapshots',
        display: '[Snapshot testing]',
    ],
    // [
    //     id: 'common',
    //     display: 'Common',
    //     permissions: [
    //         'anonymous': [
    //             'hudson.model.View.Read',
    //             'hudson.model.Item.Read',
    //         ],
    //     ],
    // ],
]

dirs.each { directory ->
    folder(directory.id){
        if (directory.display) {
            displayName(directory.display)
        }
        if (directory.description) {
            description(directory.description)
        }
        if (directory.permissions) {
            authorization {
                directory.permissions.each { user, userPermissions ->
                    permissions(user, userPermissions)
                }
            }
        }
    }
}
