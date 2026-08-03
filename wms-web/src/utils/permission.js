export const hasPerm = (user, perm) => Array.isArray(user?.permissions) && user.permissions.includes(perm)
export const isAdmin = (user) => user?.role === 'ADMIN'
