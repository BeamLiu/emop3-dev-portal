/**
 * 任务管理器模块
 * 管理异步任务的状态和结果，通过 window.TaskManager 暴露
 */

(function () {
    'use strict';

    // 防止重复初始化
    if (window.TaskManager) {
        console.log('[TaskManager] Already initialized, skipping...');
        return;
    }

    const taskPool = {};
    let taskIdCounter = 1;

    function generateTaskId() {
        return 'task_' + (taskIdCounter++);
    }

    function createTask(state, content) {
        const taskId = generateTaskId();
        taskPool[taskId] = { state: state, content: content };
        console.log(`[TaskManager] Task created: ${taskId}, state: ${state}`);
        return taskId;
    }

    function getTask(taskId) {
        const task = taskPool[taskId];

        if (task) {
            console.log(`[TaskManager] Task found: ${taskId}, state: ${task.state}`);
            return task;
        }

        // 未找到任务，返回 ERROR 状态
        const availableTasks = Object.keys(taskPool);
        const errorMsg = `Task not found: ${taskId}. Available tasks: [${availableTasks.join(', ')}]. This indicates a bug - task was not registered properly.`;
        console.error(`[TaskManager] ${errorMsg}`);
        
        // 返回 ERROR 状态，前端会停止轮询并显示错误
        return {
            state: 'ERROR',
            content: JSON.stringify({
                status: { error: true, message: errorMsg },
                data: null,
                infos: []
            })
        };
    }

    function updateTask(taskId, state, content) {
        if (taskPool[taskId]) {
            taskPool[taskId] = { state: state, content: content };
            console.log(`[TaskManager] Task updated: ${taskId}, state: ${state}`);
        } else {
            console.error(`[TaskManager] Cannot update non-existent task: ${taskId}`);
        }
    }

    function removeTask(taskId) {
        if (taskPool[taskId]) {
            delete taskPool[taskId];
            console.log(`[TaskManager] Task removed: ${taskId}`);
        }
    }

    function getAllTasks() {
        return Object.keys(taskPool);
    }

    function delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // 暴露到 window
    window.TaskManager = {
        createTask: createTask,
        getTask: getTask,
        updateTask: updateTask,
        removeTask: removeTask,
        getAllTasks: getAllTasks,
        delay: delay
    };

    console.log('[TaskManager] Module loaded');
})();
