use std::sync::{Arc, Mutex};
use std::sync::mpsc::{channel, Sender, Receiver};
use std::thread;
use std::collections::VecDeque;

pub type TaskId = u64;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Priority {
    Low = 0,
    Normal = 1,
    High = 2,
    Critical = 3,
}

pub struct Task {
    id: TaskId,
    priority: Priority,
    work: Box<dyn FnOnce() + Send + 'static>,
}

impl Task {
    pub fn new<F>(id: TaskId, priority: Priority, work: F) -> Self
    where
        F: FnOnce() + Send + 'static,
    {
        Self {
            id,
            priority,
            work: Box::new(work),
        }
    }
}

struct TaskQueue {
    tasks: VecDeque<Task>,
}

impl TaskQueue {
    fn new() -> Self {
        Self {
            tasks: VecDeque::new(),
        }
    }

    fn push(&mut self, task: Task) {
        let insert_pos = self.tasks
            .iter()
            .position(|t| t.priority < task.priority)
            .unwrap_or(self.tasks.len());
        self.tasks.insert(insert_pos, task);
    }

    fn pop(&mut self) -> Option<Task> {
        self.tasks.pop_front()
    }

    fn len(&self) -> usize {
        self.tasks.len()
    }
}

enum WorkerMessage {
    NewTask(Task),
    Shutdown,
}

pub struct WorkerPool {
    workers: Vec<thread::JoinHandle<()>>,
    sender: Sender<WorkerMessage>,
    next_task_id: Arc<Mutex<TaskId>>,
}

impl WorkerPool {
    pub fn new(thread_count: usize) -> Self {
        let (sender, receiver) = channel();
        let receiver = Arc::new(Mutex::new(receiver));
        let mut workers = Vec::with_capacity(thread_count);

        for worker_id in 0..thread_count {
            let receiver = Arc::clone(&receiver);
            let handle = thread::spawn(move || {
                Self::worker_loop(worker_id, receiver);
            });
            workers.push(handle);
        }

        Self {
            workers,
            sender,
            next_task_id: Arc::new(Mutex::new(0)),
        }
    }

    fn worker_loop(worker_id: usize, receiver: Arc<Mutex<Receiver<WorkerMessage>>>) {
        loop {
            let message = {
                let receiver = receiver.lock().unwrap();
                receiver.recv()
            };

            match message {
                Ok(WorkerMessage::NewTask(task)) => {
                    (task.work)();
                }
                Ok(WorkerMessage::Shutdown) | Err(_) => {
                    break;
                }
            }
        }
    }

    pub fn submit<F>(&self, priority: Priority, work: F) -> TaskId
    where
        F: FnOnce() + Send + 'static,
    {
        let task_id = {
            let mut next_id = self.next_task_id.lock().unwrap();
            let id = *next_id;
            *next_id = next_id.wrapping_add(1);
            id
        };

        let task = Task::new(task_id, priority, work);
        self.sender.send(WorkerMessage::NewTask(task)).ok();
        task_id
    }

    pub fn thread_count(&self) -> usize {
        self.workers.len()
    }
}

impl Drop for WorkerPool {
    fn drop(&mut self) {
        for _ in 0..self.workers.len() {
            self.sender.send(WorkerMessage::Shutdown).ok();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::time::Duration;

    #[test]
    fn worker_pool_executes_tasks() {
        let pool = WorkerPool::new(2);
        let counter = Arc::new(AtomicUsize::new(0));

        for _ in 0..10 {
            let counter = Arc::clone(&counter);
            pool.submit(Priority::Normal, move || {
                counter.fetch_add(1, Ordering::SeqCst);
            });
        }

        thread::sleep(Duration::from_millis(100));
        assert_eq!(counter.load(Ordering::SeqCst), 10);
    }

    #[test]
    fn task_queue_respects_priority() {
        let mut queue = TaskQueue::new();

        queue.push(Task::new(1, Priority::Low, || {}));
        queue.push(Task::new(2, Priority::High, || {}));
        queue.push(Task::new(3, Priority::Normal, || {}));

        assert_eq!(queue.pop().unwrap().id, 2);
        assert_eq!(queue.pop().unwrap().id, 3);
        assert_eq!(queue.pop().unwrap().id, 1);
    }

    #[test]
    fn worker_pool_assigns_unique_task_ids() {
        let pool = WorkerPool::new(2);
        let mut ids = Vec::new();

        for _ in 0..100 {
            ids.push(pool.submit(Priority::Normal, || {}));
        }

        ids.sort();
        ids.dedup();
        assert_eq!(ids.len(), 100);
    }
}
